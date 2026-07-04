package io.elasticmapper.parser.node;

import io.elasticmapper.parser.DynamicContext;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;

/**
 * Represents a {@code <foreach collection="list" item="item" separator=",">} tag.
 *
 * <p>Inspired by MyBatis's {@code ForEachSqlNode}:
 * <ul>
 *   <li>{@code open} is emitted once before the first item</li>
 *   <li>{@code separator} is emitted between items (before 2nd, 3rd, ...)</li>
 *   <li>{@code close} is emitted once after the last item</li>
 *   <li>Empty/null collection emits nothing (not even open/close)</li>
 * </ul>
 */
public class ForeachNode extends StatementNode {

    private final String collectionExpression;
    private final String itemName;
    private final String separator;
    private final String open;
    private final String close;

    public ForeachNode(String collectionExpression, String itemName,
                       String separator, String open, String close,
                       List<StatementNode> children) {
        super(children);
        this.collectionExpression = collectionExpression;
        this.itemName = itemName;
        this.separator = separator != null ? separator : ",";
        this.open = open != null ? open : "";
        this.close = close != null ? close : "";
    }

    public String getCollectionExpression() { return collectionExpression; }
    public String getItemName() { return itemName; }
    public String getSeparator() { return separator; }
    public String getOpen() { return open; }
    public String getClose() { return close; }

    @Override
    @SuppressWarnings("unchecked")
    public void render(DynamicContext ctx, StringBuilder out) {
        Object collection = ctx.getParam(collectionExpression);
        if (collection == null) return;

        if (collection instanceof Collection) {
            renderItems(ctx, out, ((Collection<Object>) collection).toArray());
        } else if (collection.getClass().isArray()) {
            int len = Array.getLength(collection);
            Object[] items = new Object[len];
            for (int i = 0; i < len; i++) {
                items[i] = Array.get(collection, i);
            }
            renderItems(ctx, out, items);
        }
    }

    /** Shared rendering — handles both Collection and array paths. */
    private void renderItems(DynamicContext ctx, StringBuilder out, Object[] items) {
        if (items == null || items.length == 0) return;

        applyOpen(out);

        // Scope frame: isolates foreach variables so nested loops
        // with the same item name don't corrupt the outer variable
        ctx.pushFrame();
        try {
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    out.append(separator);
                }
                ctx.pushVariable(itemName, items[i]);
                List<StatementNode> children = getChildren();
                if (children != null) {
                    for (StatementNode child : children) {
                        child.render(ctx, out);
                    }
                }
                ctx.popVariable(itemName);
            }
        } finally {
            ctx.popFrame();
        }

        applyClose(out);
    }

    private void applyOpen(StringBuilder out) {
        if (!open.isEmpty()) {
            out.append(open);
        }
    }

    private void applyClose(StringBuilder out) {
        if (!close.isEmpty()) {
            out.append(close);
        }
    }
}
