package io.elasticmapper.parser.node;

import io.elasticmapper.parser.DynamicContext;

import java.util.Collections;

/**
 * A text node that contains no placeholders (neither {@code ${}} nor {@code #{}}).
 * Rendered with zero runtime overhead — just appends the raw text.
 * Analogous to MyBatis's {@code StaticTextSqlNode}.
 */
public class StaticTextNode extends StatementNode {

    private final String text;

    public StaticTextNode(String text) {
        super(Collections.emptyList());
        this.text = text;
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        out.append(text);
    }

    @Override
    public String toString() {
        return "StaticTextNode{len=" + text.length() + '}';
    }
}
