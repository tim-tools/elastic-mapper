package io.github.timtools.elasticmapper.parser;

import io.github.timtools.elasticmapper.parser.node.*;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XMLScriptBuilder")
class XMLScriptBuilderTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("should parse select with if tags")
        void shouldParseSelectWithIf() {
            Map<String, StatementNode> stmts = parseXml(
                    "<mapper><select id=\"test\"><if test=\"name != null\">hello</if></select></mapper>");

            assertTrue(stmts.containsKey("test"));
            StatementNode root = stmts.get("test");
            assertNotNull(root);
        }

        @Test
        @DisplayName("should throw on missing id attribute")
        void shouldThrowOnMissingId() {
            assertThrows(StatementCompileException.class, () ->
                    parseXml("<mapper><select>no id</select></mapper>"));
        }

        @Test
        @DisplayName("should throw on unknown tag")
        void shouldThrowOnUnknownTag() {
            assertThrows(StatementCompileException.class, () ->
                    parseXml("<mapper><select id=\"s\"><unknown/></select></mapper>"));
        }

        @Test
        @DisplayName("should throw on non-mapper root")
        void shouldThrowOnBadRoot() {
            assertThrows(StatementCompileException.class, () ->
                    parseXml("<notamapper/>"));
        }

        @Test
        @DisplayName("should parse real test XML mapper")
        void shouldParseTestMapper() {
            XMLMapperParser parser = new XMLMapperParser();
            parser.parseResource("es-mapper/TestUserMapper.xml");
            assertEquals(4, parser.size());
            assertNotNull(parser.getStatement("TestUserMapper", "searchUsers"));
            assertNotNull(parser.getStatement("TestUserMapper", "findByIds"));
            assertNotNull(parser.getStatement("TestUserMapper", "partialUpdate"));
            assertNotNull(parser.getStatement("TestUserMapper", "conditionalSearch"));
        }
    }

    @Nested
    @DisplayName("IfNode rendering")
    class IfNodeRendering {

        @Test
        @DisplayName("should render content when test passes")
        void shouldRenderWhenTrue() {
            Map<String, Object> params = Collections.singletonMap("name", "Alice");
            DynamicContext ctx = new DynamicContext(params);

            IfNode node = new IfNode("name != null",
                    Collections.singletonList(new TextNode("{\"match\":\"Alice\"}")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("{\"match\":\"Alice\"}", out.toString());
        }

        @Test
        @DisplayName("should skip content when test fails")
        void shouldSkipWhenFalse() {
            Map<String, Object> params = Collections.singletonMap("name", null);
            DynamicContext ctx = new DynamicContext(params);

            IfNode node = new IfNode("name != null",
                    Collections.singletonList(new TextNode("hidden")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("", out.toString());
        }
    }

    @Nested
    @DisplayName("ForeachNode rendering")
    class ForeachNodeRendering {

        @Test
        @DisplayName("should iterate and join with separator")
        void shouldIterate() {
            Map<String, Object> params = new HashMap<>();
            params.put("idList", Arrays.asList("a", "b", "c"));
            DynamicContext ctx = new DynamicContext(params);

            ForeachNode node = new ForeachNode("idList", "id", ",", "", "",
                    Collections.singletonList(new TextNode("#{id}")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("\"a\",\"b\",\"c\"", out.toString());
        }

        @Test
        @DisplayName("should skip when collection is empty")
        void shouldSkipWhenEmpty() {
            Map<String, Object> params = Collections.singletonMap("idList", Collections.emptyList());
            DynamicContext ctx = new DynamicContext(params);

            ForeachNode node = new ForeachNode("idList", "id", ",", "", "",
                    Collections.singletonList(new TextNode("x")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("", out.toString());
        }
    }

    @Nested
    @DisplayName("ChooseNode rendering")
    class ChooseNodeRendering {

        @Test
        @DisplayName("should select first matching when")
        void shouldSelectFirstMatch() {
            Map<String, Object> params = Collections.singletonMap("x", "hello");
            DynamicContext ctx = new DynamicContext(params);

            ChooseNode node = new ChooseNode(Arrays.asList(
                    new WhenNode("x != null", Collections.singletonList(new TextNode("matched"))),
                    new WhenNode("y != null", Collections.singletonList(new TextNode("never"))),
                    new OtherwiseNode(Collections.singletonList(new TextNode("fallback")))
            ));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("matched", out.toString());
        }

        @Test
        @DisplayName("should fall back to otherwise")
        void shouldFallback() {
            DynamicContext ctx = new DynamicContext(Collections.emptyMap());

            ChooseNode node = new ChooseNode(Arrays.asList(
                    new WhenNode("x != null", Collections.singletonList(new TextNode("never"))),
                    new OtherwiseNode(Collections.singletonList(new TextNode("fallback")))
            ));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("fallback", out.toString());
        }
    }

    @Nested
    @DisplayName("WhereNode")
    class WhereNodeTests {

        @Test
        @DisplayName("should strip leading AND and wrap in bool")
        void shouldStripAndWrap() {
            DynamicContext ctx = new DynamicContext(Collections.emptyMap());
            WhereNode node = new WhereNode(
                    Collections.singletonList(new TextNode("AND {\"match\":{}}")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("{\"bool\": {{\"match\":{}}}}", out.toString());
        }

        @Test
        @DisplayName("should return empty when content is empty after strip")
        void shouldReturnEmpty() {
            DynamicContext ctx = new DynamicContext(Collections.emptyMap());
            WhereNode node = new WhereNode(
                    Collections.singletonList(new TextNode("   ")));

            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("", out.toString());
        }
    }

    @Nested
    @DisplayName("TextNode placeholder replacement")
    class TextNodeTests {

        @Test
        @DisplayName("should replace placeholders")
        void shouldReplacePlaceholders() {
            Map<String, Object> params = Collections.singletonMap("name", "Alice");
            DynamicContext ctx = new DynamicContext(params);

            TextNode node = new TextNode("{\"match\":{\"name\":#{name}}}");
            StringBuilder out = new StringBuilder();
            node.render(ctx, out);
            assertEquals("{\"match\":{\"name\":\"Alice\"}}", out.toString());
        }
    }

    @Nested
    @DisplayName("DynamicContext")
    class DynamicContextTests {

        @Test
        @DisplayName("should evaluate simple not-null")
        void shouldEvalNotNull() {
            DynamicContext ctx = new DynamicContext(Collections.singletonMap("x", "value"));
            assertTrue(ctx.evaluateTest("x != null"));
            assertFalse(ctx.evaluateTest("x == null"));
        }

        @Test
        @DisplayName("should evaluate comparisons")
        void shouldEvalComparisons() {
            DynamicContext ctx = new DynamicContext(Collections.singletonMap("age", 25));
            assertTrue(ctx.evaluateTest("age > 0"));
            assertTrue(ctx.evaluateTest("age >= 25"));
            assertFalse(ctx.evaluateTest("age < 0"));
        }

        @Test
        @DisplayName("should handle foreach variable scope")
        void shouldHandleForeachScope() {
            DynamicContext ctx = new DynamicContext(Collections.singletonMap("outer", "out"));
            ctx.pushVariable("item", "inner");
            assertEquals("inner", ctx.getParam("item"));
            assertEquals("out", ctx.getParam("outer"));
            ctx.popVariable("item");
            assertNull(ctx.getParam("item"));
        }
    }

    // ── helpers ──

    static Map<String, StatementNode> parseXml(String xml) {
        InputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        return new XMLScriptBuilder().parse(stream);
    }
}
