package io.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a delete-by-query method on a Mapper interface.
 * The value is an Elasticsearch query JSON used for delete-by-query.
 *
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {
 *     @Delete("{\"match\": {\"status\": \"#{status}\"}}")
 *     ESResponse deleteByStatus(@Param("status") String status);
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Delete {

    /**
     * Elasticsearch query JSON fragment for delete-by-query.
     * Supports {@code ${paramName}} and {@code #{paramName}} placeholders.
     */
    String value();
}
