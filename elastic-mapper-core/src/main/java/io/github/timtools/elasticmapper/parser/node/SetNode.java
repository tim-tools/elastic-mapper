package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;

/**
 * Represents a {@code <set>} tag. Inherits TrimNode's prefix/suffix/override
 * logic. Strips leading commas for partial update bodies.
 *
 * <p>Analogous to MyBatis's {@code SetSqlNode extends TrimSqlNode}.</p>
 */
public class SetNode extends TrimNode {

    public SetNode(List<StatementNode> children) {
        super("", "", null, ",", children);
    }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        String content = renderChildrenToString(ctx).trim();
        content = stripPrefixOverrides(content);  // strip any leading garbage
        content = stripSuffixOverrides(content);  // strip trailing comma

        if (content.isEmpty()) return;

        out.append(content);
    }
}
