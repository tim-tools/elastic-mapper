package io.github.timtools.elasticmapper.serialize;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.github.timtools.elasticmapper.annotations.JsonPath;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gson {@link TypeAdapterFactory} that resolves {@link JsonPath @AggPath}
 * annotations on bean fields.
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li>Field metadata (path arrays, presence flags) is cached per class
 *       — reflection happens once, not per object.</li>
 *   <li>When <em>all</em> fields are {@code @AggPath}, the default Gson
 *       adapter pass is skipped entirely, avoiding double-traversal.</li>
 *   <li>Paths are pre-split at registration time so runtime resolution
 *       is a simple array walk (no regex / string-split per field).</li>
 * </ul>
 *
 * <p>The remaining cost — eager tree materialisation via
 * {@code JsonParser.parseReader} — is inherent to path-based navigation
 * and is negligible compared to the ES network round-trip.
 */
public class JsonPathTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();

        FieldMeta meta = FIELD_META_CACHE.computeIfAbsent(rawType, FieldMeta::new);
        if (meta.isEmpty()) {
            return null; // let Gson handle normally
        }

        TypeAdapter<T> delegate = meta.isAllPath
                ? null   // skip delegate — every field is @AggPath
                : gson.getDelegateAdapter(this, typeToken);

        return new AggPathAdapter<>(gson, rawType, meta, delegate, this);
    }

    // ── Field metadata cache ──

    /** Keyed by bean class, populated lazily on first use. */
    private static final Map<Class<?>, FieldMeta> FIELD_META_CACHE
            = new ConcurrentHashMap<>();

    /**
     * Pre-computed metadata for a single bean class.
     * Immutable after construction — safe to share across threads.
     */
    private static class FieldMeta {
        /** Fields annotated with {@code @AggPath}, in declaration order. */
        final List<PathField> pathFields = new ArrayList<>(4);
        /** True when every declared field carries {@code @AggPath}. */
        final boolean isAllPath;

        FieldMeta(Class<?> type) {
            Field[] fields = type.getDeclaredFields();
            int pathCount = 0;
            for (Field field : fields) {
                JsonPath ann = field.getAnnotation(JsonPath.class);
                if (ann == null) continue;
                pathCount++;
                String[] segments = ann.value().split("\\.");
                field.setAccessible(true);
                pathFields.add(new PathField(field, segments));
            }
            this.isAllPath = pathCount == fields.length && pathCount > 0;
        }

        boolean isEmpty() { return pathFields.isEmpty(); }
    }

    /** A single {@code @AggPath}-annotated field. */
    private static class PathField {
        final Field field;
        final String[] segments;   // pre-split path

        PathField(Field field, String[] segments) {
            this.field = field;
            this.segments = segments;
        }
    }

    // ── Adapter ──

    private static class AggPathAdapter<T> extends TypeAdapter<T> {

        private final Gson gson;
        private final Class<?> rawType;
        private final FieldMeta meta;
        private final TypeAdapter<T> delegate;  // null when isAllPath
        private final TypeAdapterFactory self;  // for delegate-relative lookups

        AggPathAdapter(Gson gson, Class<?> rawType, FieldMeta meta,
                       TypeAdapter<T> delegate, TypeAdapterFactory self) {
            this.gson = gson;
            this.self = self;
            this.rawType = rawType;
            this.meta = meta;
            this.delegate = delegate;
        }

        @Override
        public T read(JsonReader in) throws IOException {
            // Eager tree parse — required for path navigation
            JsonElement root = com.google.gson.JsonParser.parseReader(in);

            // First pass: populate non-@AggPath fields (or skip if all are paths)
            T instance = null;
            if (delegate != null) {
                try {
                    instance = delegate.fromJsonTree(root);
                } catch (Exception e) {
                    // delegate failed — fall through to newInstance below
                }
            }
            if (instance == null) {
                instance = newInstance();
            }

            // Second pass: overlay @AggPath fields (pre-split paths, no regex)
            for (PathField pf : meta.pathFields) {
                JsonElement value = resolvePath(root, pf.segments);
                if (value == null || value.isJsonNull()) continue;

                try {
                    pf.field.set(instance, coerce(value, pf.field.getGenericType()));
                } catch (IllegalAccessException e) {
                    throw new IOException("Cannot set field " + pf.field.getName()
                            + " on " + rawType.getName(), e);
                }
            }

            return instance;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void write(JsonWriter out, T value) throws IOException {
            if (delegate != null) {
                delegate.write(out, value);
            } else {
                // Pure @JsonPath bean — use delegate-relative lookup to skip our own factory
                @SuppressWarnings("unchecked")
                TypeAdapter<T> next = (TypeAdapter<T>) gson.getDelegateAdapter(
                        self, TypeToken.get(rawType));
                next.write(out, value);
            }
        }

        // ── Path resolution (pre-split segments, no allocations) ──

        static JsonElement resolvePath(JsonElement root, String[] segments) {
            JsonElement current = root;
            for (String seg : segments) {
                if (current == null || !current.isJsonObject()) return null;
                current = current.getAsJsonObject().get(seg);
                if (current == null) return null;
            }
            return current;
        }

        // ── Coercion ──

        @SuppressWarnings("unchecked")
        private <V> V coerce(JsonElement elem, Type targetType) throws IOException {
            if (targetType == String.class) {
                return (V) (elem.isJsonPrimitive() ? elem.getAsString() : elem.toString());
            }
            if (targetType == Long.class   || targetType == long.class)   return (V) (Long)   elem.getAsLong();
            if (targetType == Integer.class || targetType == int.class) {
                long v = elem.getAsLong();
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                    throw new IOException("Value " + v + " exceeds int range for field");
                }
                return (V) (Integer) (int) v;
            }
            if (targetType == Double.class || targetType == double.class) return (V) (Double)  elem.getAsDouble();
            if (targetType == Float.class  || targetType == float.class)  return (V) (Float)   elem.getAsFloat();
            if (targetType == Boolean.class || targetType == boolean.class) return (V) (Boolean) elem.getAsBoolean();

            if (targetType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) targetType;
                if (pt.getRawType() == List.class && elem.isJsonArray()) {
                    Type itemType = pt.getActualTypeArguments()[0];
                    List<Object> list = new ArrayList<>();
                    for (JsonElement item : elem.getAsJsonArray()) {
                        list.add(coerce(item, itemType));
                    }
                    return (V) list;
                }
            }

            return gson.fromJson(elem, targetType);
        }

        @SuppressWarnings("unchecked")
        private T newInstance() {
            try {
                Constructor<?> ctor = rawType.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (T) ctor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate " + rawType.getName()
                        + " (needs a no-arg constructor)", e);
            }
        }
    }
}
