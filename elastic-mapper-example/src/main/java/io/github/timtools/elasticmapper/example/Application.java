package io.github.timtools.elasticmapper.example;

import io.github.timtools.elasticmapper.spring.scan.ESMapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ElasticMapper Example Application.
 *
 * <p>{@code @ESMapperScan} enables automatic discovery of Mapper interfaces
 * (those extending {@code BaseMapper}) in the specified package.
 * Each discovered interface is proxied and registered as a Spring bean.
 *
 * <p>XML mappers are auto-scanned from the default {@code classpath:es-mapper/}
 * directory — no extra configuration needed.
 *
 * <p>Elasticsearch connection is configured via {@code application.yml}
 * under the {@code elastic-mapper.*} properties.
 */
@SpringBootApplication
@ESMapperScan("io.github.timtools.elasticmapper.example.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
