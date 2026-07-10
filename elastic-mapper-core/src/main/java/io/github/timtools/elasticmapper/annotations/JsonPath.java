package io.github.timtools.elasticmapper.annotations;

import java.lang.annotation.*;

/**
 * Maps a Java bean field to a JSON path within the deserialized object.
 *
 * <p>When Gson deserializes a class with {@code @AggPath} fields, the
 * annotated field is populated by navigating the JSON tree along the
 * dot-notation path rather than by matching the field name directly.
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // ES response:  {"status_counts": {"buckets": [{"key": "active", "doc_count": 42}]}}
 *
 * class StatusCounts {
 *     @AggPath("status_counts.buckets")
 *     List<Bucket> buckets;                     // extracts the array directly
 *
 *     @AggPath("status_counts.doc_count_error_upper_bound")
 *     int errorBound;                           // extracts a nested scalar
 * }
 *
 * class Bucket {
 *     @AggPath("key")
 *     String status;                            // "key" → "active"
 *
 *     @AggPath("doc_count")
 *     long count;                               // "doc_count" → 42
 * }
 * }</pre>
 *
 * <p>Fields without {@code @AggPath} fall back to Gson's default behavior
 * (including {@link com.google.gson.annotations.SerializedName @SerializedName}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonPath {

    /**
     * Dot-notation JSON path from the root of the object being deserialized.
     *
     * <pre>{@code
     * "key"                    → json.get("key")
     * "nested.value"           → json.getAsJsonObject("nested").get("value")
     * "a.b.c"                  → json → a → b → c
     * }</pre>
     */
    String value();
}
