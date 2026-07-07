package io.elasticmapper.spring.scan;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables ElasticMapper Mapper interface scanning.
 * Similar to MyBatis {@code @MapperScan}.
 *
 * <pre>{@code
 * @SpringBootApplication
 * @MapperScan("com.example.mapper")
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(MapperScannerRegistrar.class)
public @interface ESMapperScan {

    /**
     * Base packages to scan for Mapper interfaces.
     */
    String[] value() default {};

    /**
     * Base packages to scan for Mapper interfaces (alias for value).
     */
    String[] basePackages() default {};
}
