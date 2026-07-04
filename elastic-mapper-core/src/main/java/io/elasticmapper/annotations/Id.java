package io.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the document ID in Elasticsearch.
 * Only one field per entity class can be annotated with {@code @Id}.
 * Supported field types: {@link String}, {@link Long}, {@link Integer}.
 *
 * <pre>{@code
 * public class User {
 *     @Id
 *     private String id;
 *     // ...
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
}
