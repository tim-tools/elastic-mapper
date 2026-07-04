package io.elasticmapper.parser;

import io.elasticmapper.parser.node.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses XML Mapper files into a tree of {@link StatementNode}s.
 * Inspired by MyBatis's {@code XMLScriptBuilder} design:
 * <ul>
 *   <li>{@link NodeHandler} strategy map for tag dispatch</li>
 *   <li>{@link MixedNode} composite for grouping sibling nodes</li>
 *   <li>{@link StaticTextNode} optimization for text without placeholders</li>
 *   <li>Recursive {@code parseDynamicTags} following MyBatis's pattern</li>
 * </ul>
 */
public final class XMLScriptBuilder {

    // ── NodeHandler strategy map (MyBatis pattern) ──

    @FunctionalInterface
    private interface NodeHandler {
        void handleNode(Element element, List<StatementNode> targetContents);
    }

    private final Map<String, NodeHandler> nodeHandlerMap = new LinkedHashMap<>();

    public XMLScriptBuilder() {
        // Initialise handler map — mirrors MyBatis's initNodeHandlerMap()
        nodeHandlerMap.put("if",        this::handleIf);
        nodeHandlerMap.put("foreach",   this::handleForeach);
        nodeHandlerMap.put("choose",    this::handleChoose);
        nodeHandlerMap.put("when",      this::handleWhen);
        nodeHandlerMap.put("otherwise", this::handleOtherwise);
        nodeHandlerMap.put("where",     this::handleWhere);
        nodeHandlerMap.put("set",       this::handleSet);
        nodeHandlerMap.put("trim",      this::handleTrim);
    }

    /**
     * Parses an XML mapper input stream and returns all statements.
     */
    public Map<String, StatementNode> parse(InputStream xmlStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlStream);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (!"mapper".equals(root.getTagName())) {
                throw new StatementCompileException(
                        "Root element must be <mapper>, but was <" + root.getTagName() + ">");
            }

            Map<String, StatementNode> statements = new LinkedHashMap<>();

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element elem = (Element) node;
                    String tagName = elem.getTagName();
                    String id = elem.getAttribute("id");

                    if (id == null || id.isEmpty()) {
                        throw new StatementCompileException(
                                "Statement <" + tagName + "> is missing required 'id' attribute");
                    }

                    // Parse children using MyBatis-style recursive pattern
                    // parseDynamicTags returns a list (compatible with recursive calls);
                    // the list always has exactly one element: either the sole child
                    // or a MixedNode wrapping multiple children
                    List<StatementNode> parsed = parseDynamicTags(elem);
                    StatementNode rootNode = (parsed.size() == 1)
                            ? parsed.get(0)
                            : new MixedNode(parsed);
                    statements.put(id, rootNode);
                }
            }

            return statements;
        } catch (StatementCompileException e) {
            throw e;
        } catch (Exception e) {
            throw new StatementCompileException("Failed to parse XML mapper: " + e.getMessage(), e);
        }
    }

    // ── MyBatis-style parseDynamicTags: text → TextNode/StaticTextNode, element → NodeHandler ──

    /**
     * Core recursive method, mirrors MyBatis's {@code XMLScriptBuilder.parseDynamicTags()}.
     * Walks all child nodes of an element, dispatching text nodes to
     * {@code TextNode/StaticTextNode} and element nodes to the {@code NodeHandler} map.
     */
    List<StatementNode> parseDynamicTags(Element parent) {
        List<StatementNode> contents = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            short nodeType = node.getNodeType();

            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                // Text node — check isDynamic() to choose representation
                String data = node.getTextContent();
                TextNode textNode = new TextNode(data);
                if (textNode.isDynamic()) {
                    contents.add(textNode);
                } else {
                    // StaticTextNode — zero runtime overhead
                    if (!data.trim().isEmpty()) {
                        contents.add(new StaticTextNode(data));
                    }
                }
            } else if (nodeType == Node.ELEMENT_NODE) {
                // Dynamic tag — dispatch via NodeHandler map
                Element element = (Element) node;
                String nodeName = element.getTagName().toLowerCase();
                NodeHandler handler = nodeHandlerMap.get(nodeName);
                if (handler == null) {
                    throw new StatementCompileException(
                            "Unknown tag <" + nodeName + "> in XML mapper. " +
                            "Supported tags: " + nodeHandlerMap.keySet());
                }
                handler.handleNode(element, contents);
            }
        }

        // Return single node or MixedNode composite (MyBatis returns MixedSqlNode)
        if (contents.size() == 1) {
            return contents;
        }
        // Wrap in MixedNode
        List<StatementNode> wrapped = new ArrayList<>();
        wrapped.add(new MixedNode(contents));
        return wrapped;
    }

    // ── NodeHandler implementations (MyBatis pattern: each calls parseDynamicTags recursively) ──

    private void handleIf(Element element, List<StatementNode> targetContents) {
        String test = getRequiredAttribute(element, "test");
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new IfNode(test, children));
    }

    private void handleForeach(Element element, List<StatementNode> targetContents) {
        String collection = getRequiredAttribute(element, "collection");
        String item = getRequiredAttribute(element, "item");
        String separator = element.getAttribute("separator");
        String open = element.getAttribute("open");
        String close = element.getAttribute("close");

        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new ForeachNode(collection, item,
                emptyToDefault(separator, ","),
                emptyToDefault(open, ""),
                emptyToDefault(close, ""),
                children));
    }

    private void handleChoose(Element element, List<StatementNode> targetContents) {
        List<StatementNode> whenNodes = new ArrayList<>();
        OtherwiseNode otherwiseNode = null;

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElem = (Element) node;
                String childTag = childElem.getTagName().toLowerCase();

                if ("when".equals(childTag)) {
                    String test = getRequiredAttribute(childElem, "test");
                    List<StatementNode> children = parseDynamicTags(childElem);
                    whenNodes.add(new WhenNode(test, children));
                } else if ("otherwise".equals(childTag)) {
                    List<StatementNode> children = parseDynamicTags(childElem);
                    otherwiseNode = new OtherwiseNode(children);
                } else {
                    throw new StatementCompileException(
                            "Unexpected tag <" + childTag + "> inside <choose>. " +
                            "Expected <when> or <otherwise>.");
                }
            }
        }
        if (otherwiseNode != null) {
            whenNodes.add(otherwiseNode);
        }
        targetContents.add(new ChooseNode(whenNodes));
    }

    private void handleWhen(Element element, List<StatementNode> targetContents) {
        // <when> should only appear inside <choose>; but parseDynamicTags may
        // encounter it standalone if misconfigured. Handle gracefully.
        String test = getRequiredAttribute(element, "test");
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new WhenNode(test, children));
    }

    private void handleOtherwise(Element element, List<StatementNode> targetContents) {
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new OtherwiseNode(children));
    }

    private void handleWhere(Element element, List<StatementNode> targetContents) {
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new WhereNode(children));
    }

    private void handleSet(Element element, List<StatementNode> targetContents) {
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new SetNode(children));
    }

    private void handleTrim(Element element, List<StatementNode> targetContents) {
        List<StatementNode> children = parseDynamicTags(element);
        targetContents.add(new TrimNode(
                element.getAttribute("prefix"),
                element.getAttribute("suffix"),
                element.getAttribute("prefixOverrides"),
                element.getAttribute("suffixOverrides"),
                children));
    }

    // ── Helpers ──

    private static String getRequiredAttribute(Element element, String attrName) {
        String value = element.getAttribute(attrName);
        if (value == null || value.isEmpty()) {
            throw new StatementCompileException(
                    "<" + element.getTagName() + "> tag requires a '" + attrName + "' attribute");
        }
        return value;
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    // ── Backward-compatible static entry point ──

    public static Map<String, StatementNode> parseStatic(InputStream xmlStream) {
        return new XMLScriptBuilder().parse(xmlStream);
    }
}
