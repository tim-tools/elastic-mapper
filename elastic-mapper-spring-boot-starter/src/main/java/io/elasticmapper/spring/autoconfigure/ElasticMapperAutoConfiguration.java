package io.elasticmapper.spring.autoconfigure;

import io.elasticmapper.binding.MapperProxyFactory;
import io.elasticmapper.binding.MapperRegistry;
import io.elasticmapper.config.ElasticMapperConfig;
import io.elasticmapper.executor.ElasticTemplate;
import io.elasticmapper.parser.XMLMapperParser;
import io.elasticmapper.plugin.InterceptorChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.util.List;

/**
 * Auto-configuration for ElasticMapper in Spring Boot.
 * Creates ElasticTemplate and MapperRegistry beans automatically.
 */
@AutoConfiguration
@ConditionalOnClass(ElasticTemplate.class)
@EnableConfigurationProperties(ElasticMapperProperties.class)
@Conditional(OnHostsConfiguredCondition.class)
public class ElasticMapperAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ElasticMapperAutoConfiguration.class);

    @Autowired
    private ElasticMapperProperties properties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    @ConditionalOnMissingBean
    public ElasticMapperConfig elasticMapperConfig() {
        log.info("Configuring ElasticMapper with hosts: {}", properties.getHosts());
        return ElasticMapperConfig.builder()
                .hosts(properties.getHosts())
                .scheme(properties.getScheme())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .connectTimeoutMs(properties.getConnectTimeoutMs())
                .socketTimeoutMs(properties.getSocketTimeoutMs())
                .maxConnTotal(properties.getMaxConnTotal())
                .maxConnPerRoute(properties.getMaxConnPerRoute())
                .maxResultWindow(properties.getMaxResultWindow())
                .dateFormat(properties.getDateFormat())
                .logDsl(properties.isLogDsl())
                .mapperXmlLocations(properties.getMapperXmlLocations())
                .entityPackages(properties.getEntityPackages())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ElasticTemplate elasticTemplate(ElasticMapperConfig config) {
        return new ElasticTemplate(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public MapperRegistry mapperRegistry() {
        return new MapperRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public XMLMapperParser xmlMapperParser() {
        XMLMapperParser parser = new XMLMapperParser();
        List<String> xmlLocations = properties.getMapperXmlLocations();
        if (xmlLocations != null && !xmlLocations.isEmpty()) {
            scanXmlMappers(parser, xmlLocations);
        }
        return parser;
    }

    @Bean
    @ConditionalOnMissingBean
    public InterceptorChain interceptorChain() {
        return new InterceptorChain();
    }

    /**
     * Scans classpath locations for XML mapper files and parses them.
     * Supports Ant-style glob patterns (e.g., "es-mapper/*.xml", "es-mapper/**.*.xml").
     * Directories are automatically expanded to "*‍*.xml" to find all XML files within them.
     */
    private void scanXmlMappers(XMLMapperParser parser, List<String> locations) {
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        for (String location : locations) {
            String pattern = toXmlPattern(location);
            try {
                Resource[] resources = resolver.getResources(pattern);
                if (resources.length == 0) {
                    log.warn("No XML mapper files found at: {}", pattern);
                    continue;
                }
                for (Resource resource : resources) {
                    String path = resolveClasspathRelativePath(resource, location);
                    parser.parseResource(path);
                    log.info("Loaded XML mapper from: {}", path);
                }
            } catch (IOException e) {
                log.warn("Could not scan XML mappers at location '{}': {}", location, e.getMessage());
            }
        }
        log.info("XML mapper scanning complete — {} statements loaded", parser.size());
    }

    /**
     * Converts a location to a classpath XML resource pattern.
     * If the location already contains wildcards, it is used as-is with classpath*: prefix.
     * Otherwise, "**‍/*.xml" is appended to find all .xml files under the directory.
     */
    static String toXmlPattern(String location) {
        // Strip any existing classpath: / classpath*: prefix first
        String clean = location;
        if (clean.startsWith("classpath*:")) {
            clean = clean.substring("classpath*:".length());
        } else if (clean.startsWith("classpath:")) {
            clean = clean.substring("classpath:".length());
        }
        // Strip leading slash for classpath-relative path
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }

        // If the location already has wildcards, use as-is (with classpath*: prefix)
        if (clean.contains("*") || clean.contains("?")) {
            return "classpath*:" + clean;
        }

        // Normalize to directory form and append **/*.xml
        if (!clean.endsWith("/")) {
            clean = clean + "/";
        }
        return "classpath*:" + clean + "**/*.xml";
    }

    /**
     * Resolves a Spring Resource URI back to a relative classpath path
     * that XMLMapperParser can understand (e.g., "es-mapper/UserMapper.xml").
     */
    static String resolveClasspathRelativePath(Resource resource, String location) {
        try {
            String uri = resource.getURI().toString();
            // Strip the classpath prefix to get a relative path
            int classpathIdx = uri.lastIndexOf("!/");
            if (classpathIdx >= 0) {
                // Inside a JAR: jar:file:/.../foo.jar!/es-mapper/UserMapper.xml
                return uri.substring(classpathIdx + 2);
            }
            // On the filesystem: try to find the location segment
            int locIdx = uri.lastIndexOf("/" + location.replace('\\', '/').replaceAll("/+$", ""));
            if (locIdx >= 0) {
                String subPath = uri.substring(locIdx + 1);
                // append any subdirectories beyond the location itself
                return subPath;
            }
            // Fallback: extract path portion after the last common resource directory
            String path = resource.getURL().getPath();
            int classesIdx = path.lastIndexOf("/classes/");
            if (classesIdx >= 0) {
                return path.substring(classesIdx + 9);
            }
            int targetIdx = path.lastIndexOf("/target/");
            if (targetIdx >= 0) {
                // e.g. /.../target/test-classes/es-mapper/UserMapper.xml
                String[] parts = path.substring(targetIdx + 8).split("/", 3);
                if (parts.length >= 3) {
                    return parts[2];
                }
            }
            // Best-effort: just return the filename
            return resource.getFilename();
        } catch (IOException e) {
            log.warn("Could not resolve classpath path for resource: {}", resource, e);
            return resource.getFilename();
        }
    }
}
