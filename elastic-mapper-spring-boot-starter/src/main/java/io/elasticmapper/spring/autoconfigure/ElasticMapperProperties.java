package io.elasticmapper.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot configuration properties for ElasticMapper.
 * Prefix: {@code elastic-mapper}.
 */
@ConfigurationProperties(prefix = "elastic-mapper")
public class ElasticMapperProperties {

    /** ES hosts in "host:port" format. */
    private List<String> hosts = new ArrayList<>();

    /** Scheme: http or https. */
    private String scheme = "http";

    /** Username for basic auth. */
    private String username;

    /** Password for basic auth. */
    private String password;

    /** Connect timeout in milliseconds. */
    private int connectTimeoutMs = 5000;

    /** Socket timeout in milliseconds. */
    private int socketTimeoutMs = 30000;

    /** Max total connections. */
    private int maxConnTotal = 30;

    /** Max connections per route. */
    private int maxConnPerRoute = 10;

    /** Max search result window. */
    private int maxResultWindow = 10000;

    /** Date format pattern. */
    private String dateFormat = "yyyy-MM-dd HH:mm:ss";

    /** Whether to log the final ES DSL before execution (for debugging). */
    private boolean logDsl = false;

    /** Mapper XML locations (classpath patterns). Defaults to "es-mapper/". */
    private List<String> mapperXmlLocations = new ArrayList<>();

    {
        mapperXmlLocations.add("es-mapper/");
    }

    /** Entity class packages to scan. */
    private List<String> entityPackages = new ArrayList<>();

    // Getters and setters
    public List<String> getHosts() { return hosts; }
    public void setHosts(List<String> hosts) { this.hosts = hosts; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getSocketTimeoutMs() { return socketTimeoutMs; }
    public void setSocketTimeoutMs(int socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }
    public int getMaxConnTotal() { return maxConnTotal; }
    public void setMaxConnTotal(int maxConnTotal) { this.maxConnTotal = maxConnTotal; }
    public int getMaxConnPerRoute() { return maxConnPerRoute; }
    public void setMaxConnPerRoute(int maxConnPerRoute) { this.maxConnPerRoute = maxConnPerRoute; }
    public int getMaxResultWindow() { return maxResultWindow; }
    public void setMaxResultWindow(int maxResultWindow) { this.maxResultWindow = maxResultWindow; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public boolean isLogDsl() { return logDsl; }
    public void setLogDsl(boolean logDsl) { this.logDsl = logDsl; }
    public List<String> getMapperXmlLocations() { return mapperXmlLocations; }
    public void setMapperXmlLocations(List<String> mapperXmlLocations) { this.mapperXmlLocations = mapperXmlLocations; }
    public List<String> getEntityPackages() { return entityPackages; }
    public void setEntityPackages(List<String> entityPackages) { this.entityPackages = entityPackages; }
}
