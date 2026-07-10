package io.github.timtools.elasticmapper.config;

import com.google.gson.TypeAdapterFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Global configuration for ElasticMapper.
 * Uses a Builder pattern for fluent construction.
 *
 * <pre>{@code
 * ElasticMapperConfig config = ElasticMapperConfig.builder()
 *     .hosts("127.0.0.1:9200")
 *     .username("elastic")
 *     .password("changeme")
 *     .build();
 * }</pre>
 */
public class ElasticMapperConfig {

    // ── Connection ──
    private final List<String> hosts;
    private final String scheme;
    private final String username;
    private final String password;

    // ── Timeouts (ms) ──
    private final int connectTimeoutMs;
    private final int socketTimeoutMs;

    // ── Connection Pool ──
    private final int maxConnTotal;
    private final int maxConnPerRoute;

    // ── Behavior ──
    private final int maxResultWindow;
    private final boolean prettyJson;
    private final boolean logDsl;
    private final String dateFormat;

    // ── Gson ──
    private final List<TypeAdapterFactory> typeAdapterFactories;

    // ── Scan ──
    private final List<String> mapperXmlLocations;
    private final List<String> entityPackages;

    private ElasticMapperConfig(Builder builder) {
        this.hosts = new ArrayList<>(builder.hosts);
        this.scheme = builder.scheme;
        this.username = builder.username;
        this.password = builder.password;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.socketTimeoutMs = builder.socketTimeoutMs;
        this.maxConnTotal = builder.maxConnTotal;
        this.maxConnPerRoute = builder.maxConnPerRoute;
        this.maxResultWindow = builder.maxResultWindow;
        this.prettyJson = builder.prettyJson;
        this.logDsl = builder.logDsl;
        this.dateFormat = builder.dateFormat;
        this.typeAdapterFactories = new ArrayList<>(builder.typeAdapterFactories);
        this.mapperXmlLocations = new ArrayList<>(builder.mapperXmlLocations);
        this.entityPackages = new ArrayList<>(builder.entityPackages);
    }

    // ── Getters ──

    public List<String> getHosts() { return hosts; }
    public String getScheme() { return scheme; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getSocketTimeoutMs() { return socketTimeoutMs; }
    public int getMaxConnTotal() { return maxConnTotal; }
    public int getMaxConnPerRoute() { return maxConnPerRoute; }
    public int getMaxResultWindow() { return maxResultWindow; }
    public boolean isPrettyJson() { return prettyJson; }
    public boolean isLogDsl() { return logDsl; }
    public String getDateFormat() { return dateFormat; }
    public List<TypeAdapterFactory> getTypeAdapterFactories() { return typeAdapterFactories; }
    public List<String> getMapperXmlLocations() { return mapperXmlLocations; }
    public List<String> getEntityPackages() { return entityPackages; }

    /**
     * Creates a new Builder with defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ElasticMapperConfig}.
     */
    public static class Builder {
        private List<String> hosts = new ArrayList<>();
        private String scheme = "http";
        private String username;
        private String password;
        private int connectTimeoutMs = 5000;
        private int socketTimeoutMs = 30000;
        private int maxConnTotal = 30;
        private int maxConnPerRoute = 10;
        private int maxResultWindow = 10000;
        private boolean prettyJson = false;
        private boolean logDsl = false;
        private String dateFormat = "yyyy-MM-dd HH:mm:ss";
        private List<TypeAdapterFactory> typeAdapterFactories = new ArrayList<>();
        private List<String> mapperXmlLocations = new ArrayList<>();
        private List<String> entityPackages = new ArrayList<>();

        /** ES hosts in "host:port" format, e.g. "127.0.0.1:9200" */
        public Builder hosts(String... hosts) {
            this.hosts = Arrays.asList(hosts);
            return this;
        }

        /** ES hosts in "host:port" format */
        public Builder hosts(List<String> hosts) {
            this.hosts = new ArrayList<>(hosts);
            return this;
        }

        /** Scheme: "http" or "https" (default: "http") */
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        /** Username for basic auth */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /** Password for basic auth */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Connect timeout in milliseconds (default: 5000) */
        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /** Socket timeout in milliseconds (default: 30000) */
        public Builder socketTimeoutMs(int socketTimeoutMs) {
            this.socketTimeoutMs = socketTimeoutMs;
            return this;
        }

        /** Max total connections (default: 30) */
        public Builder maxConnTotal(int maxConnTotal) {
            this.maxConnTotal = maxConnTotal;
            return this;
        }

        /** Max connections per route (default: 10) */
        public Builder maxConnPerRoute(int maxConnPerRoute) {
            this.maxConnPerRoute = maxConnPerRoute;
            return this;
        }

        /** ES max_result_window (default: 10000) */
        public Builder maxResultWindow(int maxResultWindow) {
            this.maxResultWindow = maxResultWindow;
            return this;
        }

        /** Pretty-print JSON for debugging (default: false) */
        public Builder prettyJson(boolean prettyJson) {
            this.prettyJson = prettyJson;
            return this;
        }

        /** Log the final ES DSL before execution (default: false) */
        public Builder logDsl(boolean logDsl) {
            this.logDsl = logDsl;
            return this;
        }

        /** Date format pattern (default: "yyyy-MM-dd HH:mm:ss") */
        public Builder dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        /** Register a custom Gson TypeAdapterFactory */
        public Builder addTypeAdapterFactory(TypeAdapterFactory factory) {
            this.typeAdapterFactories.add(factory);
            return this;
        }

        /** Set TypeAdapter factories */
        public Builder typeAdapterFactories(List<TypeAdapterFactory> factories) {
            this.typeAdapterFactories = new ArrayList<>(factories);
            return this;
        }

        /** Mapper XML file locations */
        public Builder mapperXmlLocations(List<String> locations) {
            this.mapperXmlLocations = new ArrayList<>(locations);
            return this;
        }

        /** Entity class packages to scan */
        public Builder entityPackages(List<String> packages) {
            this.entityPackages = new ArrayList<>(packages);
            return this;
        }

        public ElasticMapperConfig build() {
            if (hosts.isEmpty()) {
                throw new IllegalArgumentException("At least one ES host must be configured");
            }
            return new ElasticMapperConfig(this);
        }
    }
}
