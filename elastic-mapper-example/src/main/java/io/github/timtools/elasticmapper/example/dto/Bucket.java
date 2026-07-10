package io.github.timtools.elasticmapper.example.dto;

import io.github.timtools.elasticmapper.annotations.JsonPath;

/**
 * A single terms-aggregation bucket with path-based extraction.
 *
 * <p>Each bucket in ES looks like {@code {"key": "active", "doc_count": 42}}.
 * {@code @AggPath} maps the ES field names to our Java names.
 */
public class Bucket {

    @JsonPath("key")
    private String key;

    @JsonPath("doc_count")
    private Long docCount;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Long getDocCount() { return docCount; }
    public void setDocCount(Long docCount) { this.docCount = docCount; }
}
