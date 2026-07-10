package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents an {@code <otherwise>} tag — the fallback branch of a {@code <choose>}.
 */
public class OtherwiseNode extends StatementNode {

    public OtherwiseNode(List<StatementNode> children) {
        super(children);
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        renderChildren(ctx, out);
    }
}
