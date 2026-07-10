package io.github.timtools.elasticmapper.metadata;

import io.github.timtools.elasticmapper.annotations.Field;
import io.github.timtools.elasticmapper.annotations.Id;
import io.github.timtools.elasticmapper.annotations.IndexName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetadataParser}.
 */
@DisplayName("MetadataParser")
class MetadataParserTest {

    // ── Test entity classes ──

    @IndexName("user_index")
    static class User {
        @Id
        private String id;
        private String name;
        @Field(type = "keyword", name = "user_email")
        private String email;
        private Integer age;
        private Boolean active;
        private Double score;
        private BigDecimal balance;
        private Date createdAt;
        private LocalDate birthDate;
        private LocalDateTime updatedAt;
        @Field(type = "nested")
        private List<Address> addresses;
    }

    static class Address {
        private String city;
        private String street;
    }

    static class NoAnnotationEntity {
        private String id;
        private String userName;
        private Long count;
    }

    static class MultiIdEntity {
        @Id
        private String id1;
        @Id
        private String id2;
    }

    static class PrimitiveTypesEntity {
        @Id
        private int id;
        private long longVal;
        private double doubleVal;
        private boolean boolVal;
        private float floatVal;
        private short shortVal;
        private byte byteVal;
    }

    @IndexName("enum_test")
    static class EnumEntity {
        @Id
        private String id;
        private TestEnum status;
    }

    enum TestEnum { ACTIVE, INACTIVE }

    // ── Nested: resolve index name ──

    @Nested
    @DisplayName("Index name resolution")
    class IndexNameResolution {

        @Test
        @DisplayName("should use @IndexName annotation when present")
        void shouldUseAnnotation() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("user_index", meta.getIndexName());
        }

        @Test
        @DisplayName("should infer from class name when @IndexName is absent")
        void shouldInferFromClassName() {
            EntityMetadata meta = MetadataParser.parse(NoAnnotationEntity.class);
            assertEquals("no_annotation_entity", meta.getIndexName());
        }
    }

    // ── Nested: @Id detection ──

    @Nested
    @DisplayName("@Id detection")
    class IdDetection {

        @Test
        @DisplayName("should detect @Id annotated field")
        void shouldDetectIdField() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertNotNull(meta.getIdField());
            assertEquals("id", meta.getIdField().getJavaName());
            assertTrue(meta.getIdField().isId());
        }

        @Test
        @DisplayName("should throw when multiple @Id fields exist")
        void shouldThrowOnMultipleIds() {
            assertThrows(MetadataParseException.class,
                    () -> MetadataParser.parse(MultiIdEntity.class));
        }
    }

    // ── Nested: Field name inference ──

    @Nested
    @DisplayName("ES field name inference")
    class EsFieldNameInference {

        @Test
        @DisplayName("should use @Field name when specified")
        void shouldUseAnnotationName() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            FieldMetadata emailField = meta.getField("email");
            assertNotNull(emailField);
            assertEquals("user_email", emailField.getEsName());
        }

        @Test
        @DisplayName("should convert camelCase to snake_case by convention")
        void shouldConvertCamelToSnake() {
            EntityMetadata meta = MetadataParser.parse(NoAnnotationEntity.class);
            assertEquals("user_name", meta.getField("userName").getEsName());
        }
    }

    // ── Nested: ES type inference ──

    @Nested
    @DisplayName("ES type inference")
    class EsTypeInference {

        @Test
        @DisplayName("should infer text for String fields")
        void shouldInferTextForString() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("text", meta.getField("name").getEsType());
        }

        @Test
        @DisplayName("should use @Field type override")
        void shouldUseAnnotationTypeOverride() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("keyword", meta.getField("email").getEsType());
        }

        @Test
        @DisplayName("should infer long for Integer/Long/Short")
        void shouldInferLongForIntegers() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("long", meta.getField("age").getEsType());

            // count is a Long on NoAnnotationEntity
            EntityMetadata meta2 = MetadataParser.parse(NoAnnotationEntity.class);
            assertEquals("long", meta2.getField("count").getEsType());
        }

        @Test
        @DisplayName("should infer double for Double/Float/BigDecimal")
        void shouldInferDoubleForFloats() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("double", meta.getField("score").getEsType());
            assertEquals("double", meta.getField("balance").getEsType());
        }

        @Test
        @DisplayName("should infer boolean for Boolean")
        void shouldInferBoolean() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("boolean", meta.getField("active").getEsType());
        }

        @Test
        @DisplayName("should infer date for Date/LocalDate/LocalDateTime")
        void shouldInferDate() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            assertEquals("date", meta.getField("createdAt").getEsType());
            assertEquals("date", meta.getField("birthDate").getEsType());
            assertEquals("date", meta.getField("updatedAt").getEsType());
        }

        @Test
        @DisplayName("should infer nested for List fields")
        void shouldInferNestedForList() {
            EntityMetadata meta = MetadataParser.parse(User.class);
            FieldMetadata addrField = meta.getField("addresses");
            assertNotNull(addrField);
            assertTrue(addrField.isNested());
            assertEquals("nested", addrField.getEsType());
            assertEquals(Address.class, addrField.getNestedClass());
        }

        @Test
        @DisplayName("should infer keyword for Enum fields")
        void shouldInferKeywordForEnum() {
            EntityMetadata meta = MetadataParser.parse(EnumEntity.class);
            assertEquals("keyword", meta.getField("status").getEsType());
        }
    }

    // ── Nested: Primitive type inference ──

    @Nested
    @DisplayName("Primitive type inference")
    class PrimitiveTypeInference {

        @Test
        @DisplayName("should handle all primitive types correctly")
        void shouldHandlePrimitives() {
            EntityMetadata meta = MetadataParser.parse(PrimitiveTypesEntity.class);

            assertEquals("long", meta.getField("id").getEsType());
            assertEquals("long", meta.getField("longVal").getEsType());
            assertEquals("double", meta.getField("doubleVal").getEsType());
            assertEquals("boolean", meta.getField("boolVal").getEsType());
            assertEquals("double", meta.getField("floatVal").getEsType());
            assertEquals("long", meta.getField("shortVal").getEsType());
            assertEquals("long", meta.getField("byteVal").getEsType());
        }
    }

    // ── Nested: Static/transient field skipping ──

    @Nested
    @DisplayName("Field skipping")
    class FieldSkipping {

        @Test
        @DisplayName("should skip static and transient fields")
        void shouldSkipStaticAndTransient() {
            EntityMetadata meta = MetadataParser.parse(WithStaticAndTransient.class);
            assertNotNull(meta.getField("normal"));
            assertNull(meta.getField("staticField"));
            assertNull(meta.getField("transientField"));
        }
    }

    static class WithStaticAndTransient {
        @Id
        private String normal;
        private static String staticField = "ignored";
        private transient String transientField = "ignored";
    }

    // ── Utility method tests ──

    @Nested
    @DisplayName("camelToSnake utility")
    class CamelToSnake {

        @Test
        @DisplayName("should convert simple camelCase")
        void shouldConvertSimple() {
            assertEquals("user_name", MetadataParser.camelToSnake("userName"));
        }

        @Test
        @DisplayName("should convert PascalCase")
        void shouldConvertPascalCase() {
            assertEquals("user", MetadataParser.camelToSnake("User"));
            assertEquals("http_header", MetadataParser.camelToSnake("HTTPHeader"));
        }

        @Test
        @DisplayName("should handle already snake_case")
        void shouldHandleAlreadySnake() {
            assertEquals("already_snake", MetadataParser.camelToSnake("already_snake"));
        }

        @Test
        @DisplayName("should handle null and empty")
        void shouldHandleNullAndEmpty() {
            assertNull(MetadataParser.camelToSnake(null));
            assertEquals("", MetadataParser.camelToSnake(""));
        }
    }

    @Nested
    @DisplayName("snakeToCamel utility")
    class SnakeToCamel {

        @Test
        @DisplayName("should convert snake_case to camelCase")
        void shouldConvert() {
            assertEquals("userName", MetadataParser.snakeToCamel("user_name"));
            assertEquals("httpHeader", MetadataParser.snakeToCamel("http_header"));
        }

        @Test
        @DisplayName("should handle already camelCase")
        void shouldHandleAlreadyCamel() {
            assertEquals("alreadyCamel", MetadataParser.snakeToCamel("alreadyCamel"));
        }
    }
}
