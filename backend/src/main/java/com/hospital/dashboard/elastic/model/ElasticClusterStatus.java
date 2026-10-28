package com.hospital.dashboard.elastic.model;

import java.util.HashMap;
import java.util.Map;

public class ElasticClusterStatus {
    private boolean connectedToCluster;
    private String clusterName;
    private String clusterVersion;
    private String status; // "green", "yellow"
    private String activeMode; // "LIVE_ELASTICSEARCH" or "STANDALONE_ELASTIC_ENGINE"
    private String clusterUri;
    private Map<String, Long> indexDocCounts = new HashMap<>();
    private long totalDocuments;
    private long lastSyncTimestamp;

    public ElasticClusterStatus() {}

    public boolean isConnectedToCluster() { return connectedToCluster; }
    public void setConnectedToCluster(boolean connectedToCluster) { this.connectedToCluster = connectedToCluster; }
    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }
    public String getClusterVersion() { return clusterVersion; }
    public void setClusterVersion(String clusterVersion) { this.clusterVersion = clusterVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActiveMode() { return activeMode; }
    public void setActiveMode(String activeMode) { this.activeMode = activeMode; }
    public String getClusterUri() { return clusterUri; }
    public void setClusterUri(String clusterUri) { this.clusterUri = clusterUri; }
    public Map<String, Long> getIndexDocCounts() { return indexDocCounts; }
    public void setIndexDocCounts(Map<String, Long> indexDocCounts) { this.indexDocCounts = indexDocCounts; }
    public long getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }
    public long getLastSyncTimestamp() { return lastSyncTimestamp; }
    public void setLastSyncTimestamp(long lastSyncTimestamp) { this.lastSyncTimestamp = lastSyncTimestamp; }
}
