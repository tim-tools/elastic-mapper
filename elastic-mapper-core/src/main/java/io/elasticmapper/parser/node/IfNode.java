package io.elasticmapper.parser.node;

import io.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents an {@code <if test="...">} tag.
 * Evaluates the test expression; if true, renders child content.
 */
public class IfNode extends StatementNode {

    private final String testExpression;

    public IfNode(String testExpression, List<StatementNode> children) {
        super(children);
        this.testExpression = testExpression;
    }

    public String getTestExpression() {
        return testExpression;
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        if (ctx.evaluateTest(testExpression)) {
            renderChildren(ctx, out);
        }
    }
}
