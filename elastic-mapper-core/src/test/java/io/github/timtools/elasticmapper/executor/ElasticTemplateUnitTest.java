package io.github.timtools.elasticmapper.executor;

import io.github.timtools.elasticmapper.config.ElasticMapperConfig;
import io.github.timtools.elasticmapper.serialize.GsonFactory;
import io.github.timtools.elasticmapper.serialize.TypeAdapterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ElasticMapper components that don't require a running ES.
 */
@DisplayName("ElasticMapper Unit Tests")
class ElasticTemplateUnitTest {

    @Nested
    @DisplayName("ElasticMapperConfig")
    class Config {

        @Test
        @DisplayName("should build with minimal configuration")
        void shouldBuildWithMinimalConfig() {
            ElasticMapperConfig config = ElasticMapperConfig.builder()
                    .hosts("127.0.0.1:9200")
                    .build();

            assertEquals(1, config.getHosts().size());
            assertEquals("127.0.0.1:9200", config.getHosts().get(0));
            assertEquals("http", config.getScheme());
            assertEquals(5000, config.getConnectTimeoutMs());
            assertEquals(30000, config.getSocketTimeoutMs());
            assertEquals(30, config.getMaxConnTotal());
            assertEquals(10, config.getMaxConnPerRoute());
        }

        @Test
        @DisplayName("should build with full configuration")
        void shouldBuildWithFullConfig() {
            ElasticMapperConfig config = ElasticMapperConfig.builder()
                    .hosts("host1:9200", "host2:9200")
                    .scheme("https")
                    .username("elastic")
                    .password("secret")
                    .connectTimeoutMs(10000)
                    .socketTimeoutMs(60000)
                    .maxConnTotal(50)
                    .maxConnPerRoute(20)
                    .maxResultWindow(5000)
                    .prettyJson(true)
                    .dateFormat("yyyy/MM/dd")
                    .build();

            assertEquals(2, config.getHosts().size());
            assertEquals("https", config.getScheme());
            assertEquals("elastic", config.getUsername());
            assertEquals("secret", config.getPassword());
            assertEquals(10000, config.getConnectTimeoutMs());
            assertEquals(60000, config.getSocketTimeoutMs());
            assertEquals(50, config.getMaxConnTotal());
            assertEquals(20, config.getMaxConnPerRoute());
            assertEquals(5000, config.getMaxResultWindow());
            assertTrue(config.isPrettyJson());
            assertEquals("yyyy/MM/dd", config.getDateFormat());
        }

        @Test
        @DisplayName("should throw when no hosts configured")
        void shouldThrowOnEmptyHosts() {
            assertThrows(IllegalArgumentException.class,
                    () -> ElasticMapperConfig.builder().build());
        }
    }

    @Nested
    @DisplayName("GsonFactory")
    class Gson {

        @Test
        @DisplayName("should create default Gson instance")
        void shouldCreateDefault() {
            com.google.gson.Gson gson = GsonFactory.createDefault();
            assertNotNull(gson);

            // Verify serialization
            GsonTestBean bean = new GsonTestBean("test", 42);
            String json = gson.toJson(bean);
            assertTrue(json.contains("\"name\""));
            assertTrue(json.contains("\"test\""));
            assertTrue(json.contains("42"));
        }

        @Test
        @DisplayName("should serialize nulls")
        void shouldSerializeNulls() {
            com.google.gson.Gson gson = GsonFactory.createDefault();
            String json = gson.toJson(new GsonTestBean(null, 42));
            assertTrue(json.contains("\"name\":null") || json.contains("\"name\": null"));
        }
    }

    static class GsonTestBean {
        private String name;
        private int value;

        GsonTestBean(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @Nested
    @DisplayName("TypeAdapterRegistry")
    class TypeAdapter {

        @Test
        @DisplayName("should start with empty adapter list")
        void shouldStartEmpty() {
            TypeAdapterRegistry registry = new TypeAdapterRegistry();
            assertEquals(0, registry.size());
            assertTrue(registry.getFactories().isEmpty());
        }

        @Test
        @DisplayName("should ignore null registration")
        void shouldIgnoreNull() {
            TypeAdapterRegistry registry = new TypeAdapterRegistry();
            registry.register(null);
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("should clear adapters")
        void shouldClear() {
            TypeAdapterRegistry registry = new TypeAdapterRegistry();
            assertEquals(0, registry.size());

            registry.clear();
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("should build Gson without any custom adapters")
        void shouldBuildGson() {
            TypeAdapterRegistry registry = new TypeAdapterRegistry();
            com.google.gson.Gson gson = registry.buildGson("yyyy-MM-dd", false);
            assertNotNull(gson);
        }
    }

    @Nested
    @DisplayName("ESResponse")
    class Response {

        @Test
        @DisplayName("should report created/updated/deleted status")
        void shouldReportStatus() {
            ESResponse created = new ESResponse("idx", "1", "created", 1, true);
            assertTrue(created.isCreated());
            assertFalse(created.isUpdated());

            ESResponse updated = new ESResponse("idx", "1", "updated", 2, true);
            assertTrue(updated.isUpdated());
            assertFalse(updated.isCreated());

            ESResponse deleted = new ESResponse("idx", "1", "deleted", 3, true);
            assertTrue(deleted.isDeleted());
        }
    }

    @Nested
    @DisplayName("ElasticMapperException")
    class Exceptions {

        @Test
        @DisplayName("should include error code in message")
        void shouldIncludeErrorCode() {
            ElasticMapperException e = new ElasticMapperException("EM-CFG-001", "Hosts not configured");
            assertTrue(e.getMessage().contains("[EM-CFG-001]"));
            assertEquals("EM-CFG-001", e.getErrorCode());
        }

        @Test
        @DisplayName("ESExecutionException should carry status code")
        void shouldCarryStatusCode() {
            ESExecutionException e = new ESExecutionException("EM-ES-001", "Connection refused", 500, null);
            assertEquals(500, e.getStatusCode());
            assertEquals("EM-ES-001", e.getErrorCode());
        }

        @Test
        @DisplayName("SerializationException should wrap cause")
        void shouldWrapCause() {
            RuntimeException cause = new RuntimeException("gson error");
            SerializationException e = new SerializationException("EM-SER-001", "Deserialization failed", cause);
            assertEquals(cause, e.getCause());
        }
    }

    @Nested
    @DisplayName("ElasticTemplate.escapeJson")
    class EscapeJson {

        @Test
        @DisplayName("should escape special JSON characters")
        void shouldEscapeSpecialChars() {
            assertEquals("hello", ElasticTemplate.escapeJson("hello"));
            assertEquals("hello\\\"world", ElasticTemplate.escapeJson("hello\"world"));
            assertEquals("a\\\\b", ElasticTemplate.escapeJson("a\\b"));
        }

        @Test
        @DisplayName("should handle null")
        void shouldHandleNull() {
            assertEquals("", ElasticTemplate.escapeJson(null));
        }
    }
}
