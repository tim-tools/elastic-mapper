package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.Collections;

/**
 * Represents raw text content in the XML (the JSON being built).
 * Handles placeholder substitution ({@code ${}} and {@code #{}}).
 */
public class TextNode extends StatementNode {

    private final String text;

    public TextNode(String text) {
        super(Collections.emptyList());
        this.text = text;
    }

    public String getText() {
        return text;
    }

    /**
     * Returns true if this text contains dynamic placeholders
     * ({@code ${}} or {@code #{}}) that need runtime evaluation.
     * Analogous to MyBatis's {@code TextSqlNode.isDynamic()}.
     */
    public boolean isDynamic() {
        return text.contains("${") || text.contains("#{");
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        out.append(ctx.replacePlaceholders(text));
    }
}
