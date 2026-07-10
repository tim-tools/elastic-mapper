package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents a {@code <when test="...">} tag inside a {@code <choose>}.
 */
public class WhenNode extends StatementNode {

    private final String testExpression;

    public WhenNode(String testExpression, List<StatementNode> children) {
        super(children);
        this.testExpression = testExpression;
    }

    public String getTestExpression() {
        return testExpression;
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        renderChildren(ctx, out);
    }
}
