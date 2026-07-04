package io.elasticmapper.parser.node;

import io.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents a {@code <choose>} tag containing {@link WhenNode}s and an optional {@link OtherwiseNode}.
 */
public class ChooseNode extends StatementNode {

    public ChooseNode(List<StatementNode> children) {
        super(children);
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        for (StatementNode child : getChildren()) {
            if (child instanceof WhenNode) {
                WhenNode when = (WhenNode) child;
                if (ctx.evaluateTest(when.getTestExpression())) {
                    child.render(ctx, out);
                    return;
                }
            } else if (child instanceof OtherwiseNode) {
                child.render(ctx, out);
                return;
            }
        }
    }
}
