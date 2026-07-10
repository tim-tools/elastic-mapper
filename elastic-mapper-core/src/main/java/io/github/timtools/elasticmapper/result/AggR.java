package io.github.timtools.elasticmapper.result;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Typed Elasticsearch search response with aggregations.
 *
 * <p>Mirrors the ES {@code GET /index/_search} response shape. The fixed
 * scaffolding ({@code took}, {@code _shards}, {@code hits}) is built-in;
 * the variable {@code aggregations} payload is supplied by the caller via
 * the generic parameter {@code <A>} and deserialized entirely by Gson.
 *
 * <h3>Raw usage</h3>
 * <pre>{@code
 * Type type = new TypeToken<AggR<JsonObject>>(){}.getType();
 * AggR<JsonObject> res = template.queryAggs(index, query, type);
 * JsonObject byStatus = res.getAggregations().getAsJsonObject("by_status");
 * }</pre>
 *
 * <h3>Typed usage</h3>
 * <pre>{@code
 * // Define your aggregation shape (Gson-deserializable):
 * class MyAggs {
 *     {@code @SerializedName("by_status")}
 *     TermsResult byStatus;
 * }
 * class TermsResult {
 *     {@code @SerializedName("doc_count_error_upper_bound")}
 *     int docCountErrorUpperBound;
 *     List<TermsBucket> buckets;
 * }
 * class TermsBucket {
 *     String key;
 *     {@code @SerializedName("doc_count")}
 *     long docCount;
 * }
 *
 * Type type = new TypeToken<AggR<MyAggs>>(){}.getType();
 * AggR<MyAggs> res = template.queryAggs(index, query, type);
 * res.getAggregations().byStatus.buckets.get(0).key;  // typed!
 * }</pre>
 *
 * @param <A> the shape of the {@code aggregations} object (varies per query)
 * @see Page
 */
public class AggR<A> {

    private long took;

    @SerializedName("timed_out")
    private boolean timedOut;

    @SerializedName("_shards")
    private Shards shards;

    private Hits hits;

    private A aggregations;

    /** Creates an empty response (useful as a placeholder). */
    public AggR() {
        this.shards = new Shards();
        this.hits = new Hits();
    }

    public AggR(long took, boolean timedOut, Shards shards, Hits hits, A aggregations) {
        this.took = took;
        this.timedOut = timedOut;
        this.shards = shards != null ? shards : new Shards();
        this.hits = hits != null ? hits : new Hits();
        this.aggregations = aggregations;
    }

    // ── Getters / Setters ──

    /** Query execution time in milliseconds. */
    public long getTook() { return took; }
    public void setTook(long took) { this.took = took; }

    /** Whether the query exceeded the {@code timeout}. */
    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }

    /** Shard-level execution metadata. */
    public Shards getShards() { return shards; }
    public void setShards(Shards shards) { this.shards = shards != null ? shards : new Shards(); }

    /** Matching documents — empty list when {@code size: 0} is used. */
    public Hits getHits() { return hits; }
    public void setHits(Hits hits) { this.hits = hits != null ? hits : new Hits(); }

    /**
     * Aggregation results — the concrete type is supplied as the generic
     * parameter and deserialized by Gson. Typical choices:
     * <ul>
     *   <li>{@code JsonObject} — raw, navigate JSON manually</li>
     *   <li>{@code Map<String, MyAgg>} — keyed by aggregation name</li>
     *   <li>A custom bean with {@code @SerializedName} fields matching agg names</li>
     * </ul>
     */
    public A getAggregations() { return aggregations; }
    public void setAggregations(A aggregations) { this.aggregations = aggregations; }

    // ── Inner types: fixed ES response scaffolding ──

    /** Elasticsearch shard execution summary. */
    public static class Shards {
        private long total;
        private long successful;
        private long skipped;
        private long failed;

        public Shards() {}

        public Shards(long total, long successful, long skipped, long failed) {
            this.total = total;
            this.successful = successful;
            this.skipped = skipped;
            this.failed = failed;
        }

        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }

        public long getSuccessful() { return successful; }
        public void setSuccessful(long successful) { this.successful = successful; }

        public long getSkipped() { return skipped; }
        public void setSkipped(long skipped) { this.skipped = skipped; }

        public long getFailed() { return failed; }
        public void setFailed(long failed) { this.failed = failed; }

        @Override
        public String toString() {
            return "Shards{total=" + total + ", successful=" + successful +
                    ", skipped=" + skipped + ", failed=" + failed + '}';
        }
    }

    /** Elasticsearch hits container. */
    public static class Hits {
        private HitTotal total;

        @SerializedName("max_score")
        private Double maxScore;

        private List<HitDoc> hits;

        public Hits() {
            this.total = new HitTotal();
            this.hits = Collections.emptyList();
        }

        public Hits(HitTotal total, Double maxScore, List<HitDoc> hits) {
            this.total = total != null ? total : new HitTotal();
            this.maxScore = maxScore;
            this.hits = hits != null ? hits : Collections.emptyList();
        }

        public HitTotal getTotal() { return total; }
        public void setTotal(HitTotal total) { this.total = total != null ? total : new HitTotal(); }

        /** Highest {@code _score} among returned hits, or {@code null} when sorting by something else. */
        public Double getMaxScore() { return maxScore; }
        public void setMaxScore(Double maxScore) { this.maxScore = maxScore; }

        /** Matching documents (length ≤ {@code size}). */
        public List<HitDoc> getHits() { return hits; }
        public void setHits(List<HitDoc> hits) { this.hits = hits != null ? hits : Collections.emptyList(); }

        @Override
        public String toString() {
            return "Hits{total=" + total + ", maxScore=" + maxScore +
                    ", hitCount=" + hits.size() + '}';
        }
    }

    /** Total-hits descriptor — {@code relation} is {@code "eq"} when accurate, {@code "gte"} when a lower bound. */
    public static class HitTotal {
        private long value;
        private String relation;

        public HitTotal() {}

        public HitTotal(long value, String relation) {
            this.value = value;
            this.relation = relation;
        }

        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }

        public String getRelation() { return relation; }
        public void setRelation(String relation) { this.relation = relation; }

        @Override
        public String toString() {
            return "{value=" + value + ", relation=" + relation + '}';
        }
    }

    /** A single matched document (raw / untyped). */
    public static class HitDoc {

        @SerializedName("_index")
        private String index;

        @SerializedName("_id")
        private String id;

        @SerializedName("_score")
        private Double score;

        @SerializedName("_source")
        private JsonObject source;

        public HitDoc() {}

        public HitDoc(String index, String id, Double score, JsonObject source) {
            this.index = index;
            this.id = id;
            this.score = score;
            this.source = source;
        }

        public String getIndex() { return index; }
        public void setIndex(String index) { this.index = index; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        /** Raw {@code _source} document. */
        public JsonObject getSource() { return source; }
        public void setSource(JsonObject source) { this.source = source; }

        @Override
        public String toString() {
            return "HitDoc{index=" + index + ", id=" + id + ", score=" + score + '}';
        }
    }

    // ── toString ──

    @Override
    public String toString() {
        return "AggR{took=" + took + ", timedOut=" + timedOut +
                ", shards=" + shards +
                ", hits=" + hits +
                ", aggregations=" + (aggregations != null
                        ? aggregations.getClass().getSimpleName() : "null") + '}';
    }
}
