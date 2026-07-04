package io.elasticmapper.executor;

/**
 * Represents the response from an Elasticsearch write operation
 * (insert, update, delete).
 */
public class ESResponse {

    private final String index;
    private final String id;
    private final String result;    // "created", "updated", "deleted", "noop"
    private final long version;
    private final boolean acknowledged;

    public ESResponse(String index, String id, String result, long version, boolean acknowledged) {
        this.index = index;
        this.id = id;
        this.result = result;
        this.version = version;
        this.acknowledged = acknowledged;
    }

    // ── Getters ──

    public String getIndex() { return index; }
    public String getId() { return id; }
    public String getResult() { return result; }
    public long getVersion() { return version; }
    public boolean isAcknowledged() { return acknowledged; }

    /**
     * Returns true if the document was created (as opposed to updated).
     */
    public boolean isCreated() { return "created".equals(result); }

    /**
     * Returns true if the document was updated.
     */
    public boolean isUpdated() { return "updated".equals(result); }

    /**
     * Returns true if the document was deleted.
     */
    public boolean isDeleted() { return "deleted".equals(result); }

    @Override
    public String toString() {
        return "ESResponse{" +
                "index='" + index + '\'' +
                ", id='" + id + '\'' +
                ", result='" + result + '\'' +
                ", version=" + version +
                '}';
    }
}
