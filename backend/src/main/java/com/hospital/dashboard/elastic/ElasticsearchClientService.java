package com.hospital.dashboard.elastic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class ElasticsearchClientService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClientService.class);

    private final String clusterUri;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper objectMapper;

    public ElasticsearchClientService(
            @Value("${elasticsearch.uri:http://localhost:9200}") String clusterUri,
            @Value("${elasticsearch.connect-timeout-ms:2500}") int connectTimeoutMs,
            @Value("${elasticsearch.read-timeout-ms:4000}") int readTimeoutMs,
            ObjectMapper objectMapper) {
        this.clusterUri = clusterUri.replaceAll("/+$", "");
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.objectMapper = objectMapper;
    }

    public String getClusterUri() {
        return clusterUri;
    }

    /**
     * Pings the Elasticsearch cluster.
     * Returns true if cluster responds with HTTP 200 and a valid cluster payload.
     */
    public boolean isClusterReachable() {
        try {
            HttpResult res = execute("GET", "/", null);
            if (res.statusCode == 200 && res.body != null) {
                JsonNode root = objectMapper.readTree(res.body);
                return root.has("version") || root.has("cluster_name");
            }
            return false;
        } catch (Exception e) {
            log.debug("Elasticsearch cluster at {} is not reachable: {}", clusterUri, e.getMessage());
            return false;
        }
    }

    /**
     * Fetches cluster info (version, cluster_name, status).
     */
    public JsonNode getClusterInfo() {
        try {
            HttpResult res = execute("GET", "/", null);
            if (res.statusCode == 200) {
                return objectMapper.readTree(res.body);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Creates an index with mapping schema if it does not already exist.
     */
    public boolean createIndexWithMapping(String indexName, String mappingJson) {
        try {
            HttpResult check = execute("HEAD", "/" + indexName, null);
            if (check.statusCode == 200) {
                log.info("Elasticsearch index {} already exists.", indexName);
                return true;
            }
            HttpResult put = execute("PUT", "/" + indexName, mappingJson);
            log.info("Created Elasticsearch index {}: HTTP {}", indexName, put.statusCode);
            return put.statusCode == 200 || put.statusCode == 201;
        } catch (Exception e) {
            log.warn("Failed creating index {} on cluster: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Indexes a single document.
     */
    public boolean indexDocument(String indexName, String docId, String jsonBody) {
        try {
            String path = "/" + indexName + "/_doc/" + docId;
            HttpResult res = execute("PUT", path, jsonBody);
            return res.statusCode == 200 || res.statusCode == 201;
        } catch (Exception e) {
            log.warn("Failed indexing document {} in {}: {}", docId, indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Executes bulk indexing.
     */
    public boolean bulkIndex(String bulkPayloadNdjson) {
        try {
            HttpResult res = execute("POST", "/_bulk", bulkPayloadNdjson, "application/x-ndjson");
            return res.statusCode == 200;
        } catch (Exception e) {
            log.warn("Failed bulk indexing: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executes a search query DSL against an index.
     */
    public JsonNode executeSearch(String indexName, String queryDslJson) {
        try {
            String path = "/" + indexName + "/_search";
            HttpResult res = execute("POST", path, queryDslJson);
            if (res.statusCode == 200) {
                return objectMapper.readTree(res.body);
            } else {
                log.warn("Search against {} failed with status {}: {}", indexName, res.statusCode, res.body);
            }
        } catch (Exception e) {
            log.warn("Failed executing search query on cluster: {}", e.getMessage());
        }
        return null;
    }

    // --- Low-level HTTP helper ---

    public static class HttpResult {
        public final int statusCode;
        public final String body;

        public HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    public HttpResult execute(String method, String endpoint, String jsonBody) throws Exception {
        return execute(method, endpoint, jsonBody, "application/json");
    }

    public HttpResult execute(String method, String endpoint, String body, String contentType) throws Exception {
        String fullUrl = clusterUri + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Accept", "application/json");

        if (body != null && (method.equals("POST") || method.equals("PUT"))) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder responseBuilder = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line).append("\n");
                }
            }
        }
        return new HttpResult(status, responseBuilder.toString().trim());
    }
}
