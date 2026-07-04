package io.elasticmapper.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating configured {@link Gson} instances.
 * Thread-safe — a single Gson instance can be shared globally.
 *
 * <p>Supports:
 * <ul>
 *   <li>Custom {@link TypeAdapterFactory} registration for ES-specific types</li>
 *   <li>Custom date format</li>
 *   <li>Pretty printing (debug mode)</li>
 *   <li>Null serialization control</li>
 * </ul>
 */
public final class GsonFactory {

    private GsonFactory() {
        // factory class
    }

    /**
     * Creates a default Gson instance suitable for ES document serialization.
     * - Serializes nulls (so partial updates work correctly)
     * - Uses ISO date format by default
     * - No pretty printing (production setting)
     */
    public static Gson createDefault() {
        return create(new ArrayList<>(), "yyyy-MM-dd'T'HH:mm:ss.SSSZ", false);
    }

    /**
     * Creates a Gson instance with full configuration.
     *
     * @param typeAdapterFactories custom TypeAdapter factories to register
     * @param dateFormat           date format pattern (e.g., "yyyy-MM-dd HH:mm:ss")
     * @param prettyPrinting       whether to format output JSON for readability
     */
    public static Gson create(List<TypeAdapterFactory> typeAdapterFactories,
                              String dateFormat,
                              boolean prettyPrinting) {
        return build(typeAdapterFactories, dateFormat, prettyPrinting, true);
    }

    /**
     * Creates a Gson instance that does NOT serialize null fields.
     * Used for partial update operations where null means "leave unchanged."
     */
    public static Gson createNonNull(List<TypeAdapterFactory> typeAdapterFactories,
                                     String dateFormat,
                                     boolean prettyPrinting) {
        return build(typeAdapterFactories, dateFormat, prettyPrinting, false);
    }

    private static Gson build(List<TypeAdapterFactory> typeAdapterFactories,
                              String dateFormat,
                              boolean prettyPrinting,
                              boolean serializeNulls) {
        GsonBuilder builder = new GsonBuilder();

        // Built-in: @AggPath support (registered first so custom adapters can override)
        builder.registerTypeAdapterFactory(new JsonPathTypeAdapterFactory());

        // Register custom adapters (higher priority than built-in)
        if (typeAdapterFactories != null) {
            for (TypeAdapterFactory factory : typeAdapterFactories) {
                builder.registerTypeAdapterFactory(factory);
            }
        }

        // Date format
        if (dateFormat != null && !dateFormat.isEmpty()) {
            builder.setDateFormat(dateFormat);
        }

        // Null serialization
        if (serializeNulls) {
            builder.serializeNulls();
        }

        // Pretty printing
        if (prettyPrinting) {
            builder.setPrettyPrinting();
        }

        // Disable HTML escaping for clean JSON output
        builder.disableHtmlEscaping();

        return builder.create();
    }
}
