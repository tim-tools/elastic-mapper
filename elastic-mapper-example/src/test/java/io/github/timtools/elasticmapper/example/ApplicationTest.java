package io.github.timtools.elasticmapper.example;

import io.github.timtools.elasticmapper.example.entity.User;
import io.github.timtools.elasticmapper.example.mapper.UserMapper;
import io.github.timtools.elasticmapper.executor.ElasticTemplate;
import io.github.timtools.elasticmapper.parser.XMLMapperParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Spring Boot application context starts successfully
 * and all ElasticMapper beans are properly wired.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ElasticMapper Example Application")
class ApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ElasticTemplate elasticTemplate;

    @Autowired
    private XMLMapperParser xmlMapperParser;

    @Test
    @DisplayName("should load Spring context successfully")
    void shouldLoadContext() {
        assertNotNull(context);
        assertTrue(context.containsBean("userMapper"),
                "UserMapper should be registered as a Spring bean");
    }

    @Test
    @DisplayName("should wire UserMapper proxy")
    void shouldWireUserMapper() {
        assertNotNull(userMapper);
        // Verify it's a JDK proxy
        assertTrue(java.lang.reflect.Proxy.isProxyClass(userMapper.getClass()),
                "UserMapper should be a JDK proxy");
    }

    @Test
    @DisplayName("should wire ElasticTemplate")
    void shouldWireElasticTemplate() {
        assertNotNull(elasticTemplate);
    }

    @Test
    @DisplayName("should auto-scan and parse XML mappers from es-mapper/")
    void shouldParseXmlMappers() {
        assertNotNull(xmlMapperParser);
        int statementCount = xmlMapperParser.size();
        assertTrue(statementCount > 0,
                "XML mapper parser should contain statements from es-mapper/." +
                " Found: " + statementCount);
        System.out.println("Loaded " + statementCount + " XML statements");
    }
}
