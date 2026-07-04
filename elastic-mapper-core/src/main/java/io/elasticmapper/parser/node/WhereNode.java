package io.elasticmapper.parser.node;

import io.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents a {@code <where>} tag. Inherits TrimNode's prefix/suffix/override
 * logic. Strips leading AND/OR/commas and wraps non-empty content in
 * {@code {"bool": {}}}.
 *
 * <p>Analogous to MyBatis's {@code WhereSqlNode extends TrimSqlNode}.</p>
 */
public class WhereNode extends TrimNode {

    private static final String AND_OR_PATTERN = "AND |OR |AND\n|OR\n|,";

    public WhereNode(List<StatementNode> children) {
        super("{\"bool\": {", "}}", AND_OR_PATTERN, AND_OR_PATTERN, children);
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        String content = renderChildrenToString(ctx).trim();
        content = stripPrefixOverrides(content);
        content = stripSuffixOverrides(content);

        if (content.isEmpty()) return;

        out.append(getPrefix()).append(content).append(getSuffix());
    }
}
