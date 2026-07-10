package io.github.timtools.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the Elasticsearch index name for an entity class.
 * If not present, the index name is inferred from the class name
 * by converting camel-case to snake_case.
 *
 * <pre>{@code
 * @IndexName("user_index")
 * public class User {
 *     // ...
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IndexName {

    /**
     * The Elasticsearch index name.
     * Supports property placeholders when used with Spring Boot,
     * e.g. {@code "${es.index.prefix}-user"}.
     */
    String value();
}
