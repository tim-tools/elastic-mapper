package io.github.timtools.elasticmapper.parser.node;

import io.github.timtools.elasticmapper.parser.DynamicContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents a {@code <trim>} tag with prefix/suffix/prefixOverrides/suffixOverrides.
 * Also serves as the base class for {@link WhereNode} and {@link SetNode},
 * mirroring MyBatis's {@code TrimSqlNode}.
 *
 * <p>prefixOverrides and suffixOverrides are pipe-separated patterns
 * (e.g., {@code "AND |OR |,"}) that are stripped from the leading/trailing
 * content if present.</p>
 */
public class TrimNode extends StatementNode {

    private final String prefix;
    private final String suffix;
    private final String[] prefixTokens;   // pre-split — set at XML parse time, immutable
    private final String[] suffixTokens;

    public TrimNode(String prefix, String suffix, String prefixOverrides,
                    String suffixOverrides, List<StatementNode> children) {
        super(children);
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.prefixTokens = (prefixOverrides != null && !prefixOverrides.isEmpty())
                ? prefixOverrides.split("\\|") : new String[0];
        this.suffixTokens = (suffixOverrides != null && !suffixOverrides.isEmpty())
                ? suffixOverrides.split("\\|") : new String[0];
    }

    public String getPrefix() { return prefix; }
    public String getSuffix() { return suffix; }

    @Override
    public void render(DynamicContext ctx, StringBuilder out) {
        String content = renderChildrenToString(ctx).trim();
        content = stripPrefixOverrides(content);
        content = stripSuffixOverrides(content);

        if (content.isEmpty()) return;

        out.append(prefix).append(content).append(suffix);
    }

    /**
     * Strips any of the pipe-separated prefix override tokens from the content.
     * E.g., content "AND {\"match\":}" with overrides "AND |OR |," → "{\"match\":}"
     */
    protected String stripPrefixOverrides(String content) {
        for (String token : prefixTokens) {
            String t = token.trim();
            if (!t.isEmpty() && content.startsWith(t)) {
                return content.substring(t.length()).trim();
            }
            // Also handle newline variant: "AND\n" matches "AND\n..."
            String tFlat = t.replace("\\n", "\n");
            if (!tFlat.equals(t) && content.startsWith(tFlat)) {
                return content.substring(tFlat.length()).trim();
            }
        }
        return content;
    }

    /**
     * Strips any of the pipe-separated suffix override tokens from the content.
     */
    protected String stripSuffixOverrides(String content) {
        for (String token : suffixTokens) {
            String t = token.trim();
            if (!t.isEmpty()) {
                if (content.endsWith(t)) {
                    return content.substring(0, content.length() - t.length()).trim();
                }
                String tFlat = t.replace("\\n", "\n");
                if (!tFlat.equals(t) && content.endsWith(tFlat)) {
                    return content.substring(0, content.length() - tFlat.length()).trim();
                }
            }
        }
        return content;
    }

    /**
     * Repeatedly strips leading tokens until stable (for composite Like "AND AND {\"match\":}").
     */
    protected String stripLeadingRepeatedly(String content) {
        String prev;
        do {
            prev = content;
            content = stripPrefixOverrides(content);
        } while (!content.equals(prev));
        return content;
    }

    /**
     * Repeatedly strips trailing tokens until stable.
     */
    protected String stripTrailingRepeatedly(String content) {
        String prev;
        do {
            prev = content;
            content = stripSuffixOverrides(content);
        } while (!content.equals(prev));
        return content;
    }
}
