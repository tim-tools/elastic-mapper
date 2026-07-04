package io.elasticmapper.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Paginated result container supporting two modes:
 *
 * <h3>from/size — 传统分页（&lt; 10,000 条）</h3>
 * <pre>{@code
 * Page<User> page = new Page<>(1, 20);
 * page = userMapper.selectPage(page);
 * page.getTotal();     // 命中总数
 * page.getRecords();   // 当前页数据
 * page.hasNext();      // current < pages
 * page.nextPage();     // 自动 current+1
 * }</pre>
 *
 * <h3>search_after — 深分页（&gt; 10,000 条）</h3>
 * <pre>{@code
 * Page<User> page = Page.searchAfter(20, "createdAt:desc", "_id:asc");
 * page = userMapper.selectPage(page);
 * // searchAfterValues 已被 queryPage 更新为下一页游标
 * page.nextPage();     // 基于更新后的 searchAfterValues 创建下一页
 * }</pre>
 *
 * @param <T> the entity type
 */
public class Page<T> {

    public static final long MAX_RESULT_WINDOW = 10000;

    public enum Mode { FROM_SIZE, SEARCH_AFTER }

    private final Mode mode;
    private long size;
    private List<T> records;
    private long total = -1;
    private long pages;
    private boolean hasNext;

    // from/size
    private long current;

    // search_after — 查询前是入参游标，查询后被 queryPage() 覆盖为下一页游标
    private List<Object> searchAfterValues;
    private List<String> sortFields;

    // ── 构造器 ──

    /** 创建 from/size 分页。 */
    public Page(long current, long size) {
        this.mode = Mode.FROM_SIZE;
        this.current = Math.max(1, current);
        this.size = Math.max(1, size);
        this.records = Collections.emptyList();
    }

    private Page(Mode mode, long size, long current,
                 List<Object> searchAfterValues, List<String> sortFields) {
        this.mode = mode;
        this.size = size;
        this.current = current;
        this.searchAfterValues = searchAfterValues != null
                ? new ArrayList<>(searchAfterValues) : null;
        this.sortFields = sortFields != null
                ? new ArrayList<>(sortFields) : null;
        this.records = Collections.emptyList();
    }

    /** 创建 search_after 首页。 */
    public static <T> Page<T> searchAfter(long size, String... sortFields) {
        return new Page<>(Mode.SEARCH_AFTER, size, 0, null,
                sortFields.length > 0 ? new ArrayList<>(java.util.Arrays.asList(sortFields)) : null);
    }

    /** 在已有 search_after 分页上手动指定游标（通常用 {@link #nextPage()} 即可）。 */
    public static <T> Page<T> searchAfter(long size, List<String> sortFields,
                                           List<Object> searchAfterValues) {
        return new Page<>(Mode.SEARCH_AFTER, size, 0, searchAfterValues, sortFields);
    }

    // ── 翻页 ──

    /**
     * 创建下一页。
     * <ul>
     *   <li>from/size：current + 1</li>
     *   <li>search_after：取当前页的 searchAfterValues（已被 queryPage 更新为下一页游标）</li>
     * </ul>
     */
    public Page<T> nextPage() {
        if (mode == Mode.FROM_SIZE) {
            return new Page<>(current + 1, size);
        }
        if (searchAfterValues == null || searchAfterValues.isEmpty()) {
            throw new IllegalStateException(
                    "No search_after cursor available. Ensure the query returned results with a sort.");
        }
        return new Page<>(Mode.SEARCH_AFTER, size, 0, searchAfterValues, sortFields);
    }

    // ── 偏移量 ──

    /** from/size 模式的 offset，search_after 模式固定返回 0。 */
    public long from() {
        return mode == Mode.SEARCH_AFTER ? 0 : (current - 1) * size;
    }

    // ── 窗口检测 ──

    /** from + size 是否超出默认 max_result_window (10000)。 */
    public boolean exceedsMaxResultWindow() {
        return exceedsMaxResultWindow(MAX_RESULT_WINDOW);
    }

    public boolean exceedsMaxResultWindow(long maxWindow) {
        return mode == Mode.FROM_SIZE && from() + size > maxWindow;
    }

    // ── JSON 构建 ──

    /** 构建 search_after JSON 数组，如 {@code [1630000000000, "user_123"]}。 */
    public String buildSearchAfterJson() {
        if (searchAfterValues == null || searchAfterValues.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < searchAfterValues.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonValue(searchAfterValues.get(i)));
        }
        return sb.append("]").toString();
    }

    /** 构建 sort JSON 数组。 */
    public String buildSortJson() {
        if (sortFields == null || sortFields.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sortFields.size(); i++) {
            if (i > 0) sb.append(",");
            String field = sortFields.get(i);
            int colon = field.lastIndexOf(':');
            if (colon > 0) {
                sb.append("{\"").append(field.substring(0, colon).trim())
                  .append("\":\"").append(field.substring(colon + 1).trim()).append("\"}");
            } else {
                sb.append("\"").append(field.trim()).append("\"");
            }
        }
        return sb.append("]").toString();
    }

    // ── Getters / Setters ──

    public Mode getMode() { return mode; }
    public boolean isSearchAfterMode() { return mode == Mode.SEARCH_AFTER; }
    public boolean isFromSizeMode() { return mode == Mode.FROM_SIZE; }

    public long getTotal() { return total; }
    public void setTotal(long total) {
        this.total = total;
        if (total > 0 && size > 0) this.pages = (total + size - 1) / size;
    }

    public long getPages() { return pages; }
    public long getCurrent() { return current; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }

    /** 查询前：入参游标；查询后：被 queryPage() 覆盖为下一页游标。 */
    public List<Object> getSearchAfterValues() { return searchAfterValues; }
    public void setSearchAfterValues(List<Object> values) { this.searchAfterValues = values; }

    public List<String> getSortFields() { return sortFields; }
    public void setSortFields(List<String> sortFields) { this.sortFields = sortFields; }

    /** @deprecated Use {@link #hasNext()} instead. */
    @Deprecated
    public boolean isHasNext() { return hasNext(); }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean hasNext() {
        if (mode == Mode.FROM_SIZE && total >= 0) return current < pages;
        return hasNext;
    }

    // ── Helper ──

    private static String toJsonValue(Object val) {
        if (val == null) return "null";
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        return "\"" + escapeJson(val.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Page{mode=").append(mode)
                .append(", size=").append(size);
        if (mode == Mode.FROM_SIZE) sb.append(", current=").append(current);
        sb.append(", total=").append(total)
          .append(", pages=").append(pages)
          .append(", recordCount=").append(records != null ? records.size() : 0);
        if (mode == Mode.SEARCH_AFTER && searchAfterValues != null)
            sb.append(", cursor=").append(searchAfterValues);
        return sb.append('}').toString();
    }
}
