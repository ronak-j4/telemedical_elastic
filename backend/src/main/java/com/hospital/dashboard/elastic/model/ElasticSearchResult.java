package com.hospital.dashboard.elastic.model;

import java.util.List;
import java.util.Map;

public class ElasticSearchResult<T> {
    private long total;
    private long tookMs;
    private List<T> hits;
    private Map<String, Object> aggregations;
    private Map<String, Map<String, Long>> facets;
    private String queryDsl;
    private String sqlEquivalent;
    private String workloadType;
    private String clusterStatus; // "LIVE_ELASTICSEARCH" or "STANDALONE_ELASTIC_ENGINE"

    public ElasticSearchResult() {}

    public ElasticSearchResult(long total, long tookMs, List<T> hits) {
        this.total = total;
        this.tookMs = tookMs;
        this.hits = hits;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getTookMs() { return tookMs; }
    public void setTookMs(long tookMs) { this.tookMs = tookMs; }
    public List<T> getHits() { return hits; }
    public void setHits(List<T> hits) { this.hits = hits; }
    public Map<String, Object> getAggregations() { return aggregations; }
    public void setAggregations(Map<String, Object> aggregations) { this.aggregations = aggregations; }
    public Map<String, Map<String, Long>> getFacets() { return facets; }
    public void setFacets(Map<String, Map<String, Long>> facets) { this.facets = facets; }
    public String getQueryDsl() { return queryDsl; }
    public void setQueryDsl(String queryDsl) { this.queryDsl = queryDsl; }
    public String getSqlEquivalent() { return sqlEquivalent; }
    public void setSqlEquivalent(String sqlEquivalent) { this.sqlEquivalent = sqlEquivalent; }
    public String getWorkloadType() { return workloadType; }
    public void setWorkloadType(String workloadType) { this.workloadType = workloadType; }
    public String getClusterStatus() { return clusterStatus; }
    public void setClusterStatus(String clusterStatus) { this.clusterStatus = clusterStatus; }
}
