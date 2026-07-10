package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Composite node that holds a list of child StatementNodes.
 * Analogous to MyBatis's {@code MixedSqlNode}.
 * Renders by iterating all children in order.
 */
public class MixedNode extends StatementNode {

    public MixedNode(List<StatementNode> children) {
        super(children);
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        for (StatementNode child : getChildren()) {
            child.render(ctx, out);
        }
    }
}
