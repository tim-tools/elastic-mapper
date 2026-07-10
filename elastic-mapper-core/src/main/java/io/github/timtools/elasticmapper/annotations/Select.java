package io.github.timtools.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a search/query method on a Mapper interface.
 * The value is an Elasticsearch JSON query body with optional
 * placeholder substitution ({@code ${param}} / {@code #{param}}).
 *
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {
 *     @Select("{\"match\": {\"name\": \"#{name}\"}}")
 *     List<User> findByName(@Param("name") String name);
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Select {

    /**
     * Elasticsearch query JSON fragment.
     * Supports {@code ${paramName}} (template substitution) and
     * {@code #{paramName}} (safe binding with type-aware quoting).
     */
    String value();
}
