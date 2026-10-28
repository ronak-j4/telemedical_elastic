package com.hospital.dashboard.elastic.model;

public class ElasticBenchmarkResult {
    private String workloadId;
    private String workloadTitle;
    private String description;
    private String sqlQuery;
    private double sqlDurationMs;
    private int sqlResultCount;
    private String elasticQueryDsl;
    private double elasticDurationMs;
    private int elasticResultCount;
    private String speedup;
    private String architecturalAdvantage;

    public ElasticBenchmarkResult() {}

    public String getWorkloadId() { return workloadId; }
    public void setWorkloadId(String workloadId) { this.workloadId = workloadId; }
    public String getWorkloadTitle() { return workloadTitle; }
    public void setWorkloadTitle(String workloadTitle) { this.workloadTitle = workloadTitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSqlQuery() { return sqlQuery; }
    public void setSqlQuery(String sqlQuery) { this.sqlQuery = sqlQuery; }
    public double getSqlDurationMs() { return sqlDurationMs; }
    public void setSqlDurationMs(double sqlDurationMs) { this.sqlDurationMs = sqlDurationMs; }
    public int getSqlResultCount() { return sqlResultCount; }
    public void setSqlResultCount(int sqlResultCount) { this.sqlResultCount = sqlResultCount; }
    public String getElasticQueryDsl() { return elasticQueryDsl; }
    public void setElasticQueryDsl(String elasticQueryDsl) { this.elasticQueryDsl = elasticQueryDsl; }
    public double getElasticDurationMs() { return elasticDurationMs; }
    public void setElasticDurationMs(double elasticDurationMs) { this.elasticDurationMs = elasticDurationMs; }
    public int getElasticResultCount() { return elasticResultCount; }
    public void setElasticResultCount(int elasticResultCount) { this.elasticResultCount = elasticResultCount; }
    public String getSpeedup() { return speedup; }
    public void setSpeedup(String speedup) { this.speedup = speedup; }
    public String getArchitecturalAdvantage() { return architecturalAdvantage; }
    public void setArchitecturalAdvantage(String architecturalAdvantage) { this.architecturalAdvantage = architecturalAdvantage; }
}
