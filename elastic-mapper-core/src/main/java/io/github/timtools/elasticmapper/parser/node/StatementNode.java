package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Abstract node in the dynamic statement AST.
 * Each XML tag (<if>, <foreach>, etc.) is parsed into a concrete subclass.
 */
public abstract class StatementNode {

    private final List<StatementNode> children;

    protected StatementNode(List<StatementNode> children) {
        this.children = children;
    }

    public List<StatementNode> getChildren() {
        return children;
    }

    /**
     * Renders this node (and all children) into the output buffer.
     *
     * @param ctx the dynamic context containing parameter bindings
     * @param out the output buffer to append to
     */
    public abstract void render(DynamicContext ctx, StringBuilder out);

    /**
     * Renders all child nodes.
     */
    protected void renderChildren(DynamicContext ctx, StringBuilder out) {
        if (children != null) {
            for (StatementNode child : children) {
                child.render(ctx, out);
            }
        }
    }

    /**
     * Renders children and returns their combined output as a string.
     */
    protected String renderChildrenToString(DynamicContext ctx) {
        StringBuilder sb = new StringBuilder();
        renderChildren(ctx, sb);
        return sb.toString();
    }
}
