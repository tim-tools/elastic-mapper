package io.elasticmapper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a method parameter to a named placeholder in
 * {@link Select}, {@link Update}, or {@link Delete} annotations.
 *
 * <pre>{@code
 * @Select("{\"match\": {\"name\": \"#{name}\"}}")
 * List<User> findByName(@Param("name") String userName);
 * }</pre>
 *
 * When not present, the parameter name is obtained from the compiled
 * bytecode ({@code -parameters} compiler flag) or by reflection.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {

    /**
     * The parameter name used in placeholders.
     */
    String value();
}
