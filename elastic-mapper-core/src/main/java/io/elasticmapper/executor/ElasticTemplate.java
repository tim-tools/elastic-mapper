package io.elasticmapper.executor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.elasticmapper.config.ElasticMapperConfig;
import io.elasticmapper.metadata.EntityMetadata;
import io.elasticmapper.metadata.MetadataParser;
import io.elasticmapper.result.AggR;
import io.elasticmapper.result.Page;
import io.elasticmapper.serialize.GsonFactory;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core executor that wraps the Elasticsearch Low Level REST Client.
 * Provides basic CRUD operations (insert, selectById, selectByIds,
 * updateById, deleteById) against ES indices.
 *
 * <p>Implements {@link Closeable} — call {@link #close()} to release
 * the underlying {@link RestClient} connection pool.</p>
 */
public class ElasticTemplate implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ElasticTemplate.class);

    private final RestClient restClient;
    private final Gson gson;
    /** Gson instance that skips null fields — used for partial updates. */
    private final Gson gsonNonNull;
    private final ElasticMapperConfig config;
    private final int maxResultWindow;

    public ElasticTemplate(ElasticMapperConfig config) {
        this.config = config;
        this.restClient = buildRestClient(config);
        this.gson = GsonFactory.create(
                config.getTypeAdapterFactories(),
                config.getDateFormat(),
                config.isPrettyJson());
        this.gsonNonNull = GsonFactory.createNonNull(
                config.getTypeAdapterFactories(),
                config.getDateFormat(),
                config.isPrettyJson());
        this.maxResultWindow = config.getMaxResultWindow();
        log.info("ElasticTemplate initialized with hosts: {}", config.getHosts());
    }

    // ── Build RestClient ──

    private static RestClient buildRestClient(ElasticMapperConfig config) {
        HttpHost[] hosts = config.getHosts().stream()
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts);

        // Auth
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder
                            .setDefaultCredentialsProvider(credentialsProvider)
                            .setMaxConnTotal(config.getMaxConnTotal())
                            .setMaxConnPerRoute(config.getMaxConnPerRoute())
                            .setDefaultRequestConfig(RequestConfig.custom()
                                    .setConnectTimeout(config.getConnectTimeoutMs())
                                    .setSocketTimeout(config.getSocketTimeoutMs())
                                    .build()));
        } else {
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder
                            .setMaxConnTotal(config.getMaxConnTotal())
                            .setMaxConnPerRoute(config.getMaxConnPerRoute())
                            .setDefaultRequestConfig(RequestConfig.custom()
                                    .setConnectTimeout(config.getConnectTimeoutMs())
                                    .setSocketTimeout(config.getSocketTimeoutMs())
                                    .build()));
        }

        return builder.build();
    }

    // ── Insert ──

    /**
     * Inserts a single document. Uses the entity's {@code @Id} field value
     * as the ES document ID, or auto-generates one if null.
     *
     * @param index       the ES index name
     * @param entity      the document to insert
     * @param entityClass the entity class (for metadata)
     * @return the ES response
     */
    public <T> ESResponse insert(String index, T entity, Class<T> entityClass) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        String jsonBody = gson.toJson(entity);

        String id = null;
        if (meta.getIdField() != null) {
            try {
                Object idValue = meta.getIdField().getJavaField().get(entity);
                if (idValue != null) {
                    id = idValue.toString();
                }
            } catch (IllegalAccessException e) {
                throw new ElasticMapperException("EM-MAP-003",
                        "Cannot access @Id field value: " + meta.getIdField().getJavaName(), e);
            }
        }

        String endpoint;
        String httpMethod;
        if (id != null && !id.isEmpty()) {
            endpoint = "/" + index + "/_doc/" + id;
            httpMethod = "PUT";
        } else {
            endpoint = "/" + index + "/_doc";
            httpMethod = "POST";
        }

        JsonObject responseJson = performRequest(httpMethod, endpoint, jsonBody);
        return parseWriteResponse(responseJson, index);
    }

    /**
     * Inserts multiple documents in a single bulk request.
     */
    public <T> ESResponse insertBatch(String index, List<T> entities, Class<T> entityClass) {
        EntityMetadata meta = MetadataParser.parse(entityClass);
        StringBuilder bulkBody = new StringBuilder();

        for (T entity : entities) {
            String id = null;
            if (meta.getIdField() != null) {
                try {
                    Object idValue = meta.getIdField().getJavaField().get(entity);
                    if (idValue != null) {
                        id = idValue.toString();
                    }
                } catch (IllegalAccessException e) {
                    throw new ElasticMapperException("EM-MAP-003",
                            "Cannot access @Id field value: " + meta.getIdField().getJavaName(), e);
                }
            }

            // Action line
            if (id != null && !id.isEmpty()) {
                bulkBody.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
            } else {
                bulkBody.append("{\"index\":{}}\n");
            }

            // Document line
            bulkBody.append(gson.toJson(entity)).append("\n");
        }

        JsonObject responseJson = performRequest("POST", "/" + index + "/_bulk", bulkBody.toString());

        // Check for errors in bulk response
        if (responseJson.has("errors") && responseJson.get("errors").getAsBoolean()) {
            log.warn("Bulk insert had errors: {}", responseJson);
        }

        return new ESResponse(index, null, "bulk", 0, !responseJson.has("errors")
                || !responseJson.get("errors").getAsBoolean());
    }

    // ── Select ──

    /**
     * Retrieves a single document by ID.
     *
     * @return the deserialized entity, or null if not found
     */
    public <T> T selectById(String index, String id, Class<T> entityClass) {
        try {
            JsonObject responseJson = performRequest("GET", "/" + index + "/_doc/" + id, null);
            // Check if document exists
            if (responseJson.has("found") && !responseJson.get("found").getAsBoolean()) {
                return null;
            }
            JsonObject source = responseJson.getAsJsonObject("_source");
            if (source == null) {
                return null;
            }
            return gson.fromJson(source, entityClass);
        } catch (ESExecutionException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Retrieves multiple documents by ID using _mget API.
     */
    public <T> List<T> selectByIds(String index, List<String> ids, Class<T> entityClass) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder mgetBody = new StringBuilder("{\"ids\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) mgetBody.append(",");
            mgetBody.append("\"").append(escapeJson(ids.get(i))).append("\"");
        }
        mgetBody.append("]}");

        JsonObject responseJson = performRequest("POST", "/" + index + "/_mget", mgetBody.toString());
        List<T> results = new ArrayList<>();

        if (responseJson.has("docs")) {
            for (JsonElement docElem : responseJson.getAsJsonArray("docs")) {
                JsonObject doc = docElem.getAsJsonObject();
                if (doc.has("found") && doc.get("found").getAsBoolean()
                        && doc.has("_source")) {
                    results.add(gson.fromJson(doc.getAsJsonObject("_source"), entityClass));
                }
            }
        }

        return results;
    }

    /**
     * Queries documents using a raw ES JSON body.
     *
     * @param index       the ES index name
     * @param jsonBody    the ES search JSON body
     * @param entityClass the entity class to deserialize results into
     * @return list of matching documents
     */
    public <T> List<T> query(String index, String jsonBody, Class<T> entityClass) {
        JsonObject responseJson = performRequest("POST", "/" + index + "/_search", jsonBody);
        return extractHits(responseJson, entityClass);
    }

    /**
     * Queries with pagination, supporting both from/size and search_after modes.
     */
    public <T> Page<T> queryPage(String index, String jsonBody, Page<T> page, Class<T> entityClass) {
        JsonObject responseJson = performRequest("POST", "/" + index + "/_search", jsonBody);

        // Extract hits + sort values (needed for search_after cursor)
        List<T> records = new ArrayList<>();
        List<List<Object>> hitSortValues = new ArrayList<>();

        if (responseJson.has("hits")) {
            JsonObject hits = responseJson.getAsJsonObject("hits");

            // Parse total
            if (hits.has("total")) {
                JsonElement totalElem = hits.get("total");
                if (totalElem.isJsonObject()) {
                    page.setTotal(totalElem.getAsJsonObject().get("value").getAsLong());
                } else {
                    page.setTotal(totalElem.getAsLong());
                }
            }

            // Parse hits + sort
            if (hits.has("hits")) {
                for (JsonElement hitElem : hits.getAsJsonArray("hits")) {
                    JsonObject hit = hitElem.getAsJsonObject();
                    if (hit.has("_source")) {
                        records.add(gson.fromJson(hit.getAsJsonObject("_source"), entityClass));
                    }
                    // Capture sort values for search_after
                    if (page.isSearchAfterMode() && hit.has("sort")) {
                        List<Object> sortVals = new ArrayList<>();
                        for (JsonElement se : hit.getAsJsonArray("sort")) {
                            sortVals.add(jsonElementToObject(se));
                        }
                        hitSortValues.add(sortVals);
                    }
                }
            }
        }

        page.setRecords(records);

        // For search_after: overwrite searchAfterValues with next cursor from last hit
        if (page.isSearchAfterMode() && !hitSortValues.isEmpty()) {
            page.setSearchAfterValues(hitSortValues.get(hitSortValues.size() - 1));
            page.setHasNext(records.size() >= page.getSize());
        } else if (page.isFromSizeMode()) {
            page.setHasNext(page.getCurrent() < page.getPages());
        } else {
            page.setHasNext(records.size() >= page.getSize());
        }

        return page;
    }

    private static Object jsonElementToObject(JsonElement e) {
        if (e.isJsonNull()) return null;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
        if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        return e.getAsString();
    }

    /**
     * Queries aggregations and deserializes the full ES response into a
     * typed {@link AggR} via Gson. The caller supplies a {@link Type}
     * carrying the complete generic type — use {@code TypeToken} to
     * capture the {@code aggregations} shape at compile time.
     *
     * <h3>Raw usage</h3>
     * <pre>{@code
     * AggR<JsonObject> res = template.queryAggs(index, query,
     *     new TypeToken<AggR<JsonObject>>(){}.getType());
     * JsonObject aggs = res.getAggregations();
     * }</pre>
     *
     * <h3>Typed usage</h3>
     * <pre>{@code
     * AggR<MyAggs> res = template.queryAggs(index, query,
     *     new TypeToken<AggR<MyAggs>>(){}.getType());
     * res.getAggregations().byStatus.buckets.get(0).key;  // typed
     * }</pre>
     *
     * @param index    the ES index name
     * @param jsonBody the ES search JSON body with aggregations
     * @param type     the full generic return type from {@code TypeToken}
     * @param <T>      convenience type parameter matching the Type
     * @return fully deserialized ES response
     */
    @SuppressWarnings("unchecked")
    public <T> AggR<T> queryAggs(String index, String jsonBody, Type type) {
        JsonObject responseJson = performRequest("POST", "/" + index + "/_search", jsonBody);
        return (AggR<T>) gson.fromJson(responseJson, type);
    }

    // ── Update ──

    /**
     * Full document update — replaces the entire document by ID.
     * Uses {@code PUT /index/_doc/{id}} which overwrites all fields.
     * Null fields in the entity WILL overwrite existing values.
     *
     * @param entity the entity with the ID and fields to update (nulls included)
     */
    public <T> ESResponse updateById(String index, String id, T entity, Class<T> entityClass) {
        String jsonBody = gson.toJson(entity);
        JsonObject responseJson = performRequest("PUT", "/" + index + "/_doc/" + id, jsonBody);
        String result = responseJson.has("result")
                ? responseJson.get("result").getAsString()
                : "unknown";
        long version = responseJson.has("_version")
                ? responseJson.get("_version").getAsLong()
                : 0;
        boolean acknowledged = "updated".equals(result) || "created".equals(result);
        return new ESResponse(index, id, result, version, acknowledged);
    }

    /**
     * Partial document update — only non-null fields are written.
     * Uses {@code POST /index/_update/{id}} with a {@code "doc"} wrapper.
     * Null fields are SKIPPED (left unchanged in ES).
     *
     * @param entity the entity with the ID and non-null fields to update
     */
    public <T> ESResponse updatePartialById(String index, String id, T entity, Class<T> entityClass) {
        String jsonBody = gsonNonNull.toJson(entity);
        String updateBody = "{\"doc\":" + jsonBody + "}";
        JsonObject responseJson = performRequest("POST", "/" + index + "/_update/" + id, updateBody);
        String result = responseJson.has("result")
                ? responseJson.get("result").getAsString()
                : "unknown";
        long version = responseJson.has("_version")
                ? responseJson.get("_version").getAsLong()
                : 0;
        return new ESResponse(index, id, result, version, true);
    }

    // ── Delete ──

    /**
     * Deletes a document by ID.
     */
    public ESResponse deleteById(String index, String id) {
        JsonObject responseJson = performRequest("DELETE", "/" + index + "/_doc/" + id, null);
        String result = responseJson.has("result")
                ? responseJson.get("result").getAsString()
                : "unknown";
        long version = responseJson.has("_version")
                ? responseJson.get("_version").getAsLong()
                : 0;
        return new ESResponse(index, id, result, version, true);
    }

    /**
     * Deletes documents matching a query (delete-by-query).
     */
    public ESResponse deleteByQuery(String index, String jsonBody) {
        JsonObject responseJson = performRequest("POST", "/" + index + "/_delete_by_query", jsonBody);
        long deleted = responseJson.has("deleted")
                ? responseJson.get("deleted").getAsLong()
                : 0;
        return new ESResponse(index, null, "deleted", deleted, true);
    }

    // ── Low-level HTTP ──

    /**
     * Performs an HTTP request against the Elasticsearch REST API.
     *
     * @param method   HTTP method (GET, POST, PUT, DELETE)
     * @param endpoint API path (e.g., "/index/_doc/id")
     * @param jsonBody request body, or null for GET requests
     * @return parsed JSON response as JsonObject
     */
    public JsonObject performRequest(String method, String endpoint, String jsonBody) {
        if (config.isLogDsl() && jsonBody != null) {
            log.info("DSL → {} {} | body: {}", method, endpoint, jsonBody);
        }
        try {
            Request request = new Request(method, endpoint);
            if (jsonBody != null) {
                request.setJsonEntity(jsonBody);
            }

            Response response = restClient.performRequest(request);

            try (Reader reader = new InputStreamReader(
                    response.getEntity().getContent(), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            int statusCode = extractStatusCode(e);
            throw new ESExecutionException("EM-ES-001",
                    "ES communication error: " + e.getMessage(), statusCode, e);
        }
    }

    /**
     * Extracts the HTTP status code from an IOException.
     * The ES low-level REST client wraps non-2xx responses in {@code ResponseException}.
     */
    private static int extractStatusCode(IOException e) {
        if (e instanceof org.elasticsearch.client.ResponseException) {
            return ((org.elasticsearch.client.ResponseException) e)
                    .getResponse().getStatusLine().getStatusCode();
        }
        return -1;
    }

    /**
     * Raw access to the underlying RestClient for advanced use cases.
     */
    public RestClient getRestClient() {
        return restClient;
    }

    /**
     * Returns the configured Gson instance.
     */
    public Gson getGson() {
        return gson;
    }

    /**
     * Returns the global configuration.
     */
    public ElasticMapperConfig getConfig() {
        return config;
    }

    // ── Helpers ──

    private <T> List<T> extractHits(JsonObject searchResponse, Class<T> entityClass) {
        List<T> results = new ArrayList<>();
        if (searchResponse.has("hits")) {
            JsonObject hits = searchResponse.getAsJsonObject("hits");
            if (hits.has("hits")) {
                for (JsonElement hitElem : hits.getAsJsonArray("hits")) {
                    JsonObject hit = hitElem.getAsJsonObject();
                    if (hit.has("_source")) {
                        results.add(gson.fromJson(hit.getAsJsonObject("_source"), entityClass));
                    }
                }
            }
        }
        return results;
    }

    private ESResponse parseWriteResponse(JsonObject responseJson, String index) {
        String id = responseJson.has("_id")
                ? responseJson.get("_id").getAsString()
                : null;
        String result = responseJson.has("result")
                ? responseJson.get("result").getAsString()
                : "unknown";
        long version = responseJson.has("_version")
                ? responseJson.get("_version").getAsLong()
                : 0;
        return new ESResponse(index, id, result, version, true);
    }

    /**
     * Basic JSON string escaping.
     */
    public static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── Close ──

    @Override
    public void close() throws IOException {
        if (restClient != null) {
            log.info("Closing ElasticTemplate RestClient");
            restClient.close();
        }
    }
}
