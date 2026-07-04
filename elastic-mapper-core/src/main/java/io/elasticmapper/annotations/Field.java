package io.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the default field mapping for an entity field.
 * When not present, the field name and type are inferred by convention:
 * camelCase to snake_case for the name, and Java type to ES type mapping
 * for the type.
 *
 * <pre>{@code
 * public class User {
 *     @Field(type = "keyword", name = "user_email")
 *     private String email;
 *
 *     @Field(type = "nested")
 *     private List<Address> addresses;
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Field {

    /**
     * Elasticsearch field type.
     * Common values: "keyword", "text", "long", "integer", "double",
     * "boolean", "date", "nested", "object", "geo_point".
     * When empty, the type is inferred from the Java field type.
     */
    String type() default "";

    /**
     * Elasticsearch field name.
     * When empty, inferred from the Java field name (camelCase to snake_case).
     */
    String name() default "";

    /**
     * Whether this field should be indexed.
     * Default is true.
     */
    boolean index() default true;

    /**
     * Custom analyzer name for text fields.
     */
    String analyzer() default "";

    /**
     * Date format pattern for date fields.
     * Default uses the global date format from {@code ElasticMapperConfig}.
     */
    String format() default "";
}
