package io.github.timtools.elasticmapper.binding;

import io.github.timtools.elasticmapper.annotations.Delete;
import io.github.timtools.elasticmapper.annotations.Id;
import io.github.timtools.elasticmapper.annotations.IndexName;
import io.github.timtools.elasticmapper.annotations.Param;
import io.github.timtools.elasticmapper.annotations.Select;
import io.github.timtools.elasticmapper.annotations.Update;
import io.github.timtools.elasticmapper.config.ElasticMapperConfig;
import io.github.timtools.elasticmapper.executor.ESResponse;
import io.github.timtools.elasticmapper.executor.ElasticTemplate;
import io.github.timtools.elasticmapper.metadata.MetadataParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapperProxy")
class MapperProxyTest {

    // ── Test entities ──

    @IndexName("test_users")
    static class TestUser {
        @Id
        private String id;
        private String name;

        TestUser() {}
        TestUser(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // ── Test mapper interface ──

    interface TestUserMapper extends BaseMapper<TestUser> {
        @Select("{\"match\":{\"name\":\"#{name}\"}}")
        List<TestUser> findByName(@Param("name") String name);

        @Update("{\"script\":{\"source\":\"ctx._source.name=#{name}\"}}")
        ESResponse updateName(@Param("name") String newName);

        @Delete("{\"match\":{\"name\":\"#{name}\"}}")
        ESResponse deleteByName(@Param("name") String name);
    }

    // ── Nested: Placeholder replacement ──

    @Nested
    @DisplayName("replacePlaceholders")
    class PlaceholderReplacement {

        @Test
        @DisplayName("should replace #{param} with safe string binding")
        void shouldReplaceSafeParam() {
            String source = "{\"match\":{\"name\":#{name}}}";
            Map<String, Object> params = java.util.Collections.singletonMap("name", "Alice");
            String result = MapperProxy.replacePlaceholders(source, params);
            assertEquals("{\"match\":{\"name\":\"Alice\"}}", result);
        }

        @Test
        @DisplayName("should replace #{param} with number as literal")
        void shouldReplaceWithNumber() {
            String source = "{\"range\":{\"age\":{\"gte\":#{minAge}}}}";
            Map<String, Object> params = java.util.Collections.singletonMap("minAge", 18);
            String result = MapperProxy.replacePlaceholders(source, params);
            assertEquals("{\"range\":{\"age\":{\"gte\":18}}}", result);
        }

        @Test
        @DisplayName("should replace ${param} with direct substitution")
        void shouldReplaceWithDirect() {
            String source = "{\"sort\":[{\"${field}\":\"desc\"}]}";
            Map<String, Object> params = java.util.Collections.singletonMap("field", "createdAt");
            String result = MapperProxy.replacePlaceholders(source, params);
            assertEquals("{\"sort\":[{\"createdAt\":\"desc\"}]}", result);
        }

        @Test
        @DisplayName("should replace null with null")
        void shouldReplaceNull() {
            String source = "{\"match\":{\"name\":#{name}}}";
            Map<String, Object> params = java.util.Collections.singletonMap("name", null);
            String result = MapperProxy.replacePlaceholders(source, params);
            assertEquals("{\"match\":{\"name\":null}}", result);
        }

        @Test
        @DisplayName("should replace #{param} with list as JSON array")
        void shouldReplaceWithList() {
            String source = "{\"terms\":{\"status\":#{statusList}}}";
            Map<String, Object> params = java.util.Collections.singletonMap("statusList",
                    Arrays.asList("active", "pending"));
            String result = MapperProxy.replacePlaceholders(source, params);
            assertEquals("{\"terms\":{\"status\":[\"active\",\"pending\"]}}", result);
        }
    }

    // ── Nested: formatSafe ──

    @Nested
    @DisplayName("formatSafe")
    class FormatSafe {

        @Test
        @DisplayName("should format String with quotes")
        void shouldFormatString() {
            assertEquals("\"hello\"", MapperProxy.formatSafe("hello"));
        }

        @Test
        @DisplayName("should format Integer as literal")
        void shouldFormatInteger() {
            assertEquals("42", MapperProxy.formatSafe(42));
        }

        @Test
        @DisplayName("should format Long as literal")
        void shouldFormatLong() {
            assertEquals("100", MapperProxy.formatSafe(100L));
        }

        @Test
        @DisplayName("should format Boolean as true/false")
        void shouldFormatBoolean() {
            assertEquals("true", MapperProxy.formatSafe(true));
            assertEquals("false", MapperProxy.formatSafe(false));
        }

        @Test
        @DisplayName("should format null as null")
        void shouldFormatNull() {
            assertEquals("null", MapperProxy.formatSafe(null));
        }

        @Test
        @DisplayName("should format List as JSON array")
        void shouldFormatList() {
            List<Object> list = Arrays.asList("a", 1, true);
            assertEquals("[\"a\",1,true]", MapperProxy.formatSafe(list));
        }
    }

    // ── Nested: MapperProxyFactory ──

    @Nested
    @DisplayName("MapperProxyFactory.resolveEntityClass")
    class ResolveEntityClass {

        @Test
        @DisplayName("should resolve entity type from BaseMapper type param")
        void shouldResolveEntityType() {
            Class<?> entityClass = MapperProxyFactory.resolveEntityClass(TestUserMapper.class);
            assertEquals(TestUser.class, entityClass);
        }

        @Test
        @DisplayName("should throw for non-BaseMapper interface")
        void shouldThrowForNonBaseMapper() {
            assertThrows(IllegalArgumentException.class,
                    () -> MapperProxyFactory.resolveEntityClass(NotAMapper.class));
        }
    }

    interface NotAMapper {}

    // ── Nested: MapperRegistry ──

    @Nested
    @DisplayName("MapperRegistry")
    class Registry {

        private MapperRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new MapperRegistry();
        }

        @Test
        @DisplayName("should register and retrieve mapper proxy")
        void shouldRegisterAndRetrieve() {
            Object proxy = new Object();
            registry.register(TestUserMapper.class, proxy);
            assertTrue(registry.hasMapper(TestUserMapper.class));
            assertSame(proxy, registry.getMapper(TestUserMapper.class));
        }

        @Test
        @DisplayName("should cache method descriptors")
        void shouldCacheMethods() throws Exception {
            java.lang.reflect.Method method = TestUserMapper.class.getMethod("findByName", String.class);
            MapperMethod mapperMethod = new MapperMethod(method, MapperMethod.Type.ANNOTATION, "{}");
            registry.putMethod(TestUserMapper.class, method, mapperMethod);

            MapperMethod cached = registry.getMethod(TestUserMapper.class, method);
            assertNotNull(cached);
            assertEquals(MapperMethod.Type.ANNOTATION, cached.getType());
        }

        @Test
        @DisplayName("should return null for unknown method")
        void shouldReturnNullForUnknown() throws Exception {
            java.lang.reflect.Method method = TestUserMapper.class.getMethod("findByName", String.class);
            assertNull(registry.getMethod(TestUserMapper.class, method));
        }
    }
}
