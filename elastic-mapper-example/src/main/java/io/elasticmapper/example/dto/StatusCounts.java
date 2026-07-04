package io.elasticmapper.example.dto;

import com.google.gson.annotations.SerializedName;
import io.elasticmapper.annotations.JsonPath;

import java.util.List;

/**
 * Demonstrates {@code @AggPath} — extracts aggregation results
 * by navigating JSON paths instead of mirroring the ES structure.
 *
 * <p>Given the ES response:
 * <pre>{@code
 * "aggregations": {
 *   "status_counts": {
 *     "doc_count_error_upper_bound": 0,
 *     "sum_other_doc_count": 0,
 *     "buckets": [
 *       {"key": "active", "doc_count": 42}
 *     ]
 *   }
 * }
 * }</pre>
 */
public class StatusCounts {

    @JsonPath("status_counts.buckets")
    private List<Bucket> buckets;

    @SerializedName("status_counts")
    private StatusCount statusCounts;

    public List<Bucket> getBuckets() { return buckets; }
    public void setBuckets(List<Bucket> buckets) { this.buckets = buckets; }

    public StatusCount getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(StatusCount statusCounts) {
        this.statusCounts = statusCounts;
    }

    public static class StatusCount {
        private List<Bucket> buckets;

        public List<Bucket> getBuckets() { return buckets; }

        public void setBuckets(List<Bucket> buckets) {
            this.buckets = buckets;
        }
    }
}
