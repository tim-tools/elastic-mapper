package io.elasticmapper.serialize;

import com.google.gson.TypeAdapterFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for custom Gson {@link TypeAdapterFactory} instances.
 * Users can register adapters for ES-specific types (geo_point, ip, etc.).
 *
 * <pre>{@code
 * TypeAdapterRegistry registry = new TypeAdapterRegistry();
 * registry.register(new GeoPointAdapter());
 * Gson gson = registry.buildGson("yyyy-MM-dd HH:mm:ss", false);
 * }</pre>
 */
public class TypeAdapterRegistry {

    private final List<TypeAdapterFactory> factories = new CopyOnWriteArrayList<>();

    /**
     * Registers a custom TypeAdapter factory.
     */
    public TypeAdapterRegistry register(TypeAdapterFactory factory) {
        if (factory != null) {
            factories.add(factory);
        }
        return this;
    }

    /**
     * Registers multiple custom TypeAdapter factories.
     */
    public TypeAdapterRegistry registerAll(List<TypeAdapterFactory> newFactories) {
        if (newFactories != null) {
            for (TypeAdapterFactory factory : newFactories) {
                register(factory);
            }
        }
        return this;
    }

    /**
     * Removes a previously registered factory.
     */
    public TypeAdapterRegistry unregister(TypeAdapterFactory factory) {
        factories.remove(factory);
        return this;
    }

    /**
     * Returns an unmodifiable view of registered factories.
     */
    public List<TypeAdapterFactory> getFactories() {
        return Collections.unmodifiableList(new ArrayList<>(factories));
    }

    /**
     * Clears all registered adapters.
     */
    public void clear() {
        factories.clear();
    }

    /**
     * Number of registered adapters.
     */
    public int size() {
        return factories.size();
    }

    /**
     * Builds a configured Gson instance from the registered factories.
     */
    public com.google.gson.Gson buildGson(String dateFormat, boolean prettyPrinting) {
        return GsonFactory.create(
                Collections.unmodifiableList(new ArrayList<>(factories)),
                dateFormat,
                prettyPrinting);
    }
}
