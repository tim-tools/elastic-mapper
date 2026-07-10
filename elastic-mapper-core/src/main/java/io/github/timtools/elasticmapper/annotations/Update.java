package io.github.timtools.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an update method on a Mapper interface.
 * The value is an Elasticsearch update or update-by-query JSON body.
 *
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {
 *     @Update("{\"script\": {\"source\": \"ctx._source.status = #{status}\"}}")
 *     ESResponse updateStatus(@Param("id") String id, @Param("status") String status);
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Update {

    /**
     * Elasticsearch update JSON fragment.
     * Supports {@code ${paramName}} and {@code #{paramName}} placeholders.
     */
    String value();
}
