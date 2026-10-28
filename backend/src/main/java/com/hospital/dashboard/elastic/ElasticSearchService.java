package com.hospital.dashboard.elastic;

import com.hospital.dashboard.elastic.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core Elasticsearch service. Orchestrates all four workload types:
 *
 *  W1 — AGGREGATIONS   : consultations by status, patients by city, doctors by
 *                        specialization, revenue by payment method, avg rating/spec.
 *  W2 — FREE TEXT      : BM25-inspired scoring + fuzzy matching on symptoms,
 *                        diagnoses, and feedback comments.
 *  W3 — ATTRIBUTE LOOKUP: denormalized doctor search across specialization,
 *                        experience, rating, hospital and city — no JOINs.
 *  W4 — GEO PROXIMITY : Haversine radius filter on hospitals and patients;
 *                        results sorted by distance.
 */
@Service
public class ElasticSearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchService.class);

    private final ElasticDataStore dataStore;
    private final ElasticsearchClientService clientService;
    private final JdbcTemplate jdbcTemplate;

    // Workload DSL templates shown alongside each result for educational value
    private static final String AGG_DSL = """
        {
          "size": 0,
          "aggs": {
            "consultations_by_status": {
              "terms": { "field": "status", "size": 10 }
            },
            "avg_rating_per_specialization": {
              "terms": { "field": "doctorSpecialization" },
              "aggs": { "avg_rating": { "avg": { "field": "rating" } } }
            },
            "revenue_by_method": {
              "terms": { "field": "paymentMethod" },
              "aggs": { "total_revenue": { "sum": { "field": "paymentAmount" } } }
            }
          }
        }
        """;

    private static final String SQL_AGG_EQUIVALENT =
        "SELECT status, COUNT(*) FROM consultation GROUP BY status;\n" +
        "SELECT ds.specialization, AVG(dr.rating) FROM doctor_specialization ds\n" +
        "  JOIN doctor_rating dr ON ds.doctor_id = dr.doctor_id GROUP BY ds.specialization;\n" +
        "SELECT paid_by, SUM(payment_amount) FROM payment GROUP BY paid_by;";

    public ElasticSearchService(ElasticDataStore dataStore,
                                ElasticsearchClientService clientService,
                                JdbcTemplate jdbcTemplate) {
        this.dataStore = dataStore;
        this.clientService = clientService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // =========================================================================
    // WORKLOAD 1 — AGGREGATIONS
    // =========================================================================

    public ElasticSearchResult<Map<String, Object>> runAggregations() {
        long start = System.nanoTime();

        // Try live Elasticsearch cluster first
        if (clientService.isClusterReachable()) {
            String dsl = """
                {
                  "size": 0,
                  "aggs": {
                    "consultations_by_status": { "terms": { "field": "status", "size": 10 } },
                    "patients_by_city": { "terms": { "field": "patientCity", "size": 20 } },
                    "doctors_by_specialization": { "terms": { "field": "doctorSpecialization", "size": 20 } },
                    "revenue_by_payment_method": {
                      "terms": { "field": "paymentMethod", "size": 10 },
                      "aggs": { "total_revenue": { "sum": { "field": "paymentAmount" } } }
                    },
                    "avg_rating_per_specialization": {
                      "terms": { "field": "doctorSpecialization" },
                      "aggs": { "avg_rating": { "avg": { "field": "rating" } } }
                    }
                  }
                }
                """;
            var clusterResult = clientService.executeSearch(ElasticIndexMappings.ANALYTICS_INDEX, dsl);
            if (clusterResult != null && clusterResult.has("aggregations")) {
                long tookMs = clusterResult.has("took") ? clusterResult.get("took").asLong() : 0;
                ElasticSearchResult<Map<String, Object>> res = new ElasticSearchResult<>(0, tookMs, List.of());
                res.setAggregations(parseClusterAggs(clusterResult));
                res.setQueryDsl(dsl);
                res.setSqlEquivalent(SQL_AGG_EQUIVALENT);
                res.setWorkloadType("AGGREGATIONS");
                res.setClusterStatus("LIVE_ELASTICSEARCH");
                return res;
            }
        }

        // Standalone engine fallback
        Map<String, Object> aggs = dataStore.executeAggregations();
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        ElasticSearchResult<Map<String, Object>> result = new ElasticSearchResult<>(0, tookMs, List.of());
        result.setAggregations(aggs);
        result.setQueryDsl(AGG_DSL);
        result.setSqlEquivalent(SQL_AGG_EQUIVALENT);
        result.setWorkloadType("AGGREGATIONS");
        result.setClusterStatus("STANDALONE_ELASTIC_ENGINE");
        return result;
    }

    // =========================================================================
    // WORKLOAD 2 — FREE TEXT SEARCH
    // =========================================================================

    public ElasticSearchResult<MedicalRecordDoc> searchMedicalRecords(String query, String city) {
        long start = System.nanoTime();

        // Live cluster: full-text multi_match + highlighting
        if (clientService.isClusterReachable()) {
            String dsl = buildMedicalRecordDsl(query, city);
            var clusterResult = clientService.executeSearch(ElasticIndexMappings.RECORDS_INDEX, dsl);
            if (clusterResult != null && clusterResult.has("hits")) {
                List<MedicalRecordDoc> hits = parseClusterMedicalHits(clusterResult);
                long total = clusterResult.get("hits").get("total").get("value").asLong();
                long tookMs = clusterResult.has("took") ? clusterResult.get("took").asLong() : 0;
                ElasticSearchResult<MedicalRecordDoc> res = new ElasticSearchResult<>(total, tookMs, hits);
                res.setQueryDsl(dsl);
                res.setSqlEquivalent("SELECT * FROM medical_record WHERE LOWER(symptoms) LIKE '%" + query.toLowerCase() + "%' OR LOWER(diagnoses) LIKE '%" + query.toLowerCase() + "%'");
                res.setWorkloadType("FREE_TEXT");
                res.setClusterStatus("LIVE_ELASTICSEARCH");
                return res;
            }
        }

        // Standalone engine
        List<MedicalRecordDoc> hits = dataStore.searchMedicalRecords(query, city);
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        String dsl = buildMedicalRecordDsl(query, city);

        ElasticSearchResult<MedicalRecordDoc> result = new ElasticSearchResult<>(hits.size(), tookMs, hits);
        result.setQueryDsl(dsl);
        result.setSqlEquivalent("SELECT * FROM medical_record WHERE symptoms LIKE '%" + query + "%' OR diagnoses LIKE '%" + query + "%'");
        result.setWorkloadType("FREE_TEXT");
        result.setClusterStatus("STANDALONE_ELASTIC_ENGINE");
        return result;
    }

    public ElasticSearchResult<FeedbackDoc> searchFeedback(String query, String sentiment, Integer minRating) {
        long start = System.nanoTime();

        if (clientService.isClusterReachable()) {
            String dsl = buildFeedbackDsl(query, sentiment, minRating);
            var clusterResult = clientService.executeSearch(ElasticIndexMappings.FEEDBACK_INDEX, dsl);
            if (clusterResult != null && clusterResult.has("hits")) {
                List<FeedbackDoc> hits = parseClusterFeedbackHits(clusterResult);
                long total = clusterResult.get("hits").get("total").get("value").asLong();
                long tookMs = clusterResult.has("took") ? clusterResult.get("took").asLong() : 0;
                ElasticSearchResult<FeedbackDoc> res = new ElasticSearchResult<>(total, tookMs, hits);
                res.setQueryDsl(dsl);
                res.setSqlEquivalent("SELECT * FROM feedback WHERE feedback_comment LIKE '%" + query + "%'");
                res.setWorkloadType("FREE_TEXT");
                res.setClusterStatus("LIVE_ELASTICSEARCH");
                return res;
            }
        }

        List<FeedbackDoc> hits = dataStore.searchFeedback(query, sentiment, minRating);
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        ElasticSearchResult<FeedbackDoc> result = new ElasticSearchResult<>(hits.size(), tookMs, hits);
        result.setQueryDsl(buildFeedbackDsl(query, sentiment, minRating));
        result.setSqlEquivalent("SELECT * FROM feedback WHERE feedback_comment LIKE '%" + query + "%' AND rating >= " + (minRating != null ? minRating : 1));
        result.setWorkloadType("FREE_TEXT");
        result.setClusterStatus("STANDALONE_ELASTIC_ENGINE");
        return result;
    }

    // =========================================================================
    // WORKLOAD 3 — ATTRIBUTE LOOKUP (Denormalized)
    // =========================================================================

    public ElasticSearchResult<DoctorDoc> searchDoctors(String textQuery, String specialization,
                                                         Integer minExperience, Double minRating,
                                                         String city, String hospitalName) {
        long start = System.nanoTime();

        if (clientService.isClusterReachable()) {
            String dsl = buildDoctorAttributeDsl(textQuery, specialization, minExperience, minRating, city, hospitalName);
            var clusterResult = clientService.executeSearch(ElasticIndexMappings.DOCTORS_INDEX, dsl);
            if (clusterResult != null && clusterResult.has("hits")) {
                List<DoctorDoc> hits = parseClusterDoctorHits(clusterResult);
                long total = clusterResult.get("hits").get("total").get("value").asLong();
                long tookMs = clusterResult.has("took") ? clusterResult.get("took").asLong() : 0;
                ElasticSearchResult<DoctorDoc> res = new ElasticSearchResult<>(total, tookMs, hits);
                res.setQueryDsl(dsl);
                res.setSqlEquivalent(buildDoctorSqlEquivalent(specialization, minExperience, minRating, city));
                res.setWorkloadType("ATTRIBUTE_LOOKUP");
                res.setClusterStatus("LIVE_ELASTICSEARCH");
                return res;
            }
        }

        List<DoctorDoc> hits = dataStore.searchDoctors(textQuery, specialization, minExperience, minRating, city, hospitalName);
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        Map<String, Map<String, Long>> facets = dataStore.computeDoctorFacets(hits);

        ElasticSearchResult<DoctorDoc> result = new ElasticSearchResult<>(hits.size(), tookMs, hits);
        result.setQueryDsl(buildDoctorAttributeDsl(textQuery, specialization, minExperience, minRating, city, hospitalName));
        result.setSqlEquivalent(buildDoctorSqlEquivalent(specialization, minExperience, minRating, city));
        result.setFacets(facets);
        result.setWorkloadType("ATTRIBUTE_LOOKUP");
        result.setClusterStatus("STANDALONE_ELASTIC_ENGINE");
        return result;
    }

    // =========================================================================
    // WORKLOAD 4 — GEO PROXIMITY
    // =========================================================================

    public ElasticSearchResult<HospitalDoc> findNearbyHospitals(double lat, double lon,
                                                                  double radiusKm, String specialty) {
        long start = System.nanoTime();

        if (clientService.isClusterReachable()) {
            String dsl = buildGeoHospitalDsl(lat, lon, radiusKm, specialty);
            var clusterResult = clientService.executeSearch(ElasticIndexMappings.HOSPITALS_INDEX, dsl);
            if (clusterResult != null && clusterResult.has("hits")) {
                List<HospitalDoc> hits = parseClusterHospitalHits(clusterResult);
                long total = clusterResult.get("hits").get("total").get("value").asLong();
                long tookMs = clusterResult.has("took") ? clusterResult.get("took").asLong() : 0;
                ElasticSearchResult<HospitalDoc> res = new ElasticSearchResult<>(total, tookMs, hits);
                res.setQueryDsl(dsl);
                res.setSqlEquivalent("-- Oracle has no native geo_distance filter.\n-- Requires Haversine UDF or manual math on lat/lon columns.");
                res.setWorkloadType("GEO_PROXIMITY");
                res.setClusterStatus("LIVE_ELASTICSEARCH");
                return res;
            }
        }

        List<HospitalDoc> hits = dataStore.findHospitalsNear(lat, lon, radiusKm, specialty);
        long tookMs = (System.nanoTime() - start) / 1_000_000;

        ElasticSearchResult<HospitalDoc> result = new ElasticSearchResult<>(hits.size(), tookMs, hits);
        result.setQueryDsl(buildGeoHospitalDsl(lat, lon, radiusKm, specialty));
        result.setSqlEquivalent("-- Oracle has no geo_distance filter.\n-- Approximate workaround:\n" +
                "SELECT * FROM hospital WHERE city = '<nearest_city>';\n" +
                "-- (No actual distance computation — that's exactly why Elasticsearch geo_point shines)");
        result.setWorkloadType("GEO_PROXIMITY");
        result.setClusterStatus("STANDALONE_ELASTIC_ENGINE");
        return result;
    }

    // =========================================================================
    // CLUSTER STATUS
    // =========================================================================

    public ElasticClusterStatus getClusterStatus() {
        ElasticClusterStatus status = new ElasticClusterStatus();
        status.setClusterUri(clientService.getClusterUri());
        status.setLastSyncTimestamp(dataStore.getLastSyncTimestamp());
        status.setIndexDocCounts(dataStore.getDocCounts());
        status.setTotalDocuments(dataStore.getDocCounts().values().stream().mapToLong(Long::longValue).sum());

        boolean connected = clientService.isClusterReachable();
        status.setConnectedToCluster(connected);

        if (connected) {
            var info = clientService.getClusterInfo();
            if (info != null) {
                status.setClusterName(info.has("cluster_name") ? info.get("cluster_name").asText() : "elasticsearch");
                if (info.has("version")) {
                    status.setClusterVersion(info.get("version").get("number").asText());
                }
            }
            status.setStatus("green");
            status.setActiveMode("LIVE_ELASTICSEARCH");
        } else {
            status.setClusterName("standalone-elastic-engine");
            status.setClusterVersion("1.0-telemed");
            status.setStatus("yellow");
            status.setActiveMode("STANDALONE_ELASTIC_ENGINE");
        }

        return status;
    }

    // =========================================================================
    // DSL BUILDERS
    // =========================================================================

    private String buildMedicalRecordDsl(String query, String city) {
        List<String> filters = new ArrayList<>();
        String multiMatch = "";

        if (query != null && !query.isBlank()) {
            multiMatch = String.format("""
                "multi_match": {
                  "query": "%s",
                  "fields": ["symptoms^2", "diagnoses^1.5", "patientName"],
                  "type": "best_fields",
                  "fuzziness": "AUTO"
                }
                """, query.replace("\"", "\\\""));
        }

        if (city != null && !city.isBlank()) {
            filters.add(String.format("{\"term\": {\"patientCity\": \"%s\"}}", city));
        }

        String filterClause = filters.isEmpty() ? "" : ",\"filter\": [" + String.join(",", filters) + "]";

        String queryClause = multiMatch.isEmpty()
                ? "\"match_all\": {}"
                : multiMatch;

        return String.format("""
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [{ %s }] %s
                }
              },
              "highlight": {
                "fields": {
                  "symptoms": { "pre_tags": ["<mark>"], "post_tags": ["</mark>"] },
                  "diagnoses": { "pre_tags": ["<mark>"], "post_tags": ["</mark>"] }
                }
              }
            }
            """, queryClause, filterClause);
    }

    private String buildFeedbackDsl(String query, String sentiment, Integer minRating) {
        List<String> musts = new ArrayList<>();
        List<String> filters = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            musts.add(String.format("{\"multi_match\":{\"query\":\"%s\",\"fields\":[\"feedbackComment^2\",\"doctorName\"],\"fuzziness\":\"AUTO\"}}", query.replace("\"", "\\\"")));
        } else {
            musts.add("{\"match_all\":{}}");
        }
        if (sentiment != null && !sentiment.isBlank()) {
            filters.add(String.format("{\"term\":{\"sentiment\":\"%s\"}}", sentiment));
        }
        if (minRating != null && minRating > 0) {
            filters.add(String.format("{\"range\":{\"rating\":{\"gte\":%d}}}", minRating));
        }

        String filterClause = filters.isEmpty() ? "" : ",\"filter\":[" + String.join(",", filters) + "]";
        return String.format("""
            {
              "size": 50,
              "query": {"bool": {"must":[%s] %s}},
              "highlight": {"fields": {"feedbackComment": {"pre_tags": ["<mark>"],"post_tags": ["</mark>"]}}}
            }
            """, String.join(",", musts), filterClause);
    }

    private String buildDoctorAttributeDsl(String text, String spec, Integer minExp, Double minRating,
                                            String city, String hospital) {
        List<String> filters = new ArrayList<>();
        List<String> musts = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            musts.add(String.format("{\"multi_match\":{\"query\":\"%s\",\"fields\":[\"fullName^2\",\"specializations\",\"qualifications\"],\"fuzziness\":\"AUTO\"}}", text));
        } else {
            musts.add("{\"match_all\":{}}");
        }
        if (spec != null && !spec.isBlank()) {
            filters.add(String.format("{\"term\":{\"specializations\":\"%s\"}}", spec.toUpperCase()));
        }
        if (minExp != null && minExp > 0) {
            filters.add(String.format("{\"range\":{\"experience\":{\"gte\":%d}}}", minExp));
        }
        if (minRating != null && minRating > 0) {
            filters.add(String.format("{\"range\":{\"avgRating\":{\"gte\":%.1f}}}", minRating));
        }
        if (city != null && !city.isBlank()) {
            filters.add(String.format("{\"bool\":{\"should\":[{\"term\":{\"city\":\"%s\"}},{\"nested\":{\"path\":\"hospitals\",\"query\":{\"term\":{\"hospitals.city\":\"%s\"}}}}]}}", city, city));
        }
        if (hospital != null && !hospital.isBlank()) {
            filters.add(String.format("{\"nested\":{\"path\":\"hospitals\",\"query\":{\"match\":{\"hospitals.hospitalName\":\"%s\"}}}}", hospital));
        }

        String filterClause = filters.isEmpty() ? "" : ",\"filter\":[" + String.join(",", filters) + "]";
        return String.format("""
            {
              "size": 50,
              "query": {"bool": {"must": [%s] %s}},
              "sort": [{"experience": {"order": "desc"}}, {"avgRating": {"order": "desc"}}],
              "aggs": {
                "by_specialization": {"terms": {"field": "specializations"}},
                "by_city": {"terms": {"field": "city"}}
              }
            }
            """, String.join(",", musts), filterClause);
    }

    private String buildGeoHospitalDsl(double lat, double lon, double radiusKm, String specialty) {
        List<String> filters = new ArrayList<>();
        if (radiusKm > 0) {
            filters.add(String.format("{\"geo_distance\":{\"distance\":\"%skm\",\"location\":{\"lat\":%s,\"lon\":%s}}}",
                    radiusKm, lat, lon));
        }
        if (specialty != null && !specialty.isBlank()) {
            filters.add(String.format("{\"term\":{\"specialties\":\"%s\"}}", specialty.toUpperCase()));
        }
        String filterClause = filters.isEmpty() ? "{\"match_all\":{}}" :
                "{\"bool\":{\"filter\":[" + String.join(",", filters) + "]}}";

        return String.format("""
            {
              "size": 20,
              "query": %s,
              "sort": [
                {"_geo_distance": {"location": {"lat": %s, "lon": %s}, "order": "asc", "unit": "km"}}
              ]
            }
            """, filterClause, lat, lon);
    }

    private String buildDoctorSqlEquivalent(String spec, Integer minExp, Double minRating, String city) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT d.doctor_id, d.first_name, d.last_name, d.experience,\n");
        sb.append("       ds.specialization, AVG(dr.rating) AS avg_rating, h.name AS hospital\n");
        sb.append("FROM doctor d\n");
        sb.append("  JOIN doctor_specialization ds ON d.doctor_id = ds.doctor_id\n");
        sb.append("  JOIN doctor_rating dr ON d.doctor_id = dr.doctor_id\n");
        sb.append("  JOIN hospital_doctor hd ON d.doctor_id = hd.doctor_id\n");
        sb.append("  JOIN hospital h ON hd.hospital_id = h.hospital_id\n");
        sb.append("WHERE 1=1\n");
        if (spec != null && !spec.isBlank()) sb.append("  AND ds.specialization = '").append(spec.toUpperCase()).append("'\n");
        if (minExp != null && minExp > 0) sb.append("  AND d.experience >= ").append(minExp).append("\n");
        if (city != null && !city.isBlank()) sb.append("  AND h.city = '").append(city).append("'\n");
        sb.append("GROUP BY d.doctor_id, d.first_name, d.last_name, d.experience,\n");
        sb.append("         ds.specialization, h.name\n");
        if (minRating != null && minRating > 0) sb.append("HAVING AVG(dr.rating) >= ").append(minRating).append("\n");
        sb.append("ORDER BY d.experience DESC, avg_rating DESC;");
        return sb.toString();
    }

    // =========================================================================
    // CLUSTER RESPONSE PARSERS (basic field extraction from ES JSON nodes)
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseClusterAggs(com.fasterxml.jackson.databind.JsonNode root) {
        Map<String, Object> aggs = new LinkedHashMap<>();
        var aggsNode = root.get("aggregations");
        if (aggsNode == null) return aggs;
        aggsNode.fields().forEachRemaining(e -> {
            Map<String, Object> agg = new LinkedHashMap<>();
            var bucketsNode = e.getValue().get("buckets");
            if (bucketsNode != null) {
                List<Map<String, Object>> buckets = new ArrayList<>();
                bucketsNode.forEach(b -> {
                    Map<String, Object> bucket = new LinkedHashMap<>();
                    bucket.put("key", b.has("key") ? b.get("key").asText() : "");
                    bucket.put("doc_count", b.has("doc_count") ? b.get("doc_count").asLong() : 0L);
                    if (b.has("avg_rating")) bucket.put("avg_rating", b.get("avg_rating").get("value").asDouble());
                    if (b.has("total_revenue")) bucket.put("total_revenue", b.get("total_revenue").get("value").asDouble());
                    buckets.add(bucket);
                });
                agg.put("buckets", buckets);
            } else if (e.getValue().has("value")) {
                agg.put("value", e.getValue().get("value").asDouble());
            }
            aggs.put(e.getKey(), agg);
        });
        return aggs;
    }

    private List<MedicalRecordDoc> parseClusterMedicalHits(com.fasterxml.jackson.databind.JsonNode root) {
        List<MedicalRecordDoc> list = new ArrayList<>();
        root.get("hits").get("hits").forEach(h -> {
            var src = h.get("_source");
            if (src == null) return;
            MedicalRecordDoc doc = new MedicalRecordDoc();
            if (src.has("medicalRecordId")) doc.setMedicalRecordId(src.get("medicalRecordId").asLong());
            if (src.has("patientId")) doc.setPatientId(src.get("patientId").asLong());
            if (src.has("patientName")) doc.setPatientName(src.get("patientName").asText());
            if (src.has("patientCity")) doc.setPatientCity(src.get("patientCity").asText());
            if (src.has("symptoms")) doc.setSymptoms(src.get("symptoms").asText());
            if (src.has("diagnoses")) doc.setDiagnoses(src.get("diagnoses").asText());
            if (src.has("suggestedSpecialty")) doc.setSuggestedSpecialty(src.get("suggestedSpecialty").asText());
            doc.setSearchScore(h.has("_score") ? h.get("_score").asDouble() : 0.0);
            // Extract highlights
            if (h.has("highlight")) {
                List<String> hl = new ArrayList<>();
                h.get("highlight").fields().forEachRemaining(f ->
                    f.getValue().forEach(v -> hl.add(v.asText())));
                doc.setMatchHighlights(hl);
            }
            list.add(doc);
        });
        return list;
    }

    private List<FeedbackDoc> parseClusterFeedbackHits(com.fasterxml.jackson.databind.JsonNode root) {
        List<FeedbackDoc> list = new ArrayList<>();
        root.get("hits").get("hits").forEach(h -> {
            var src = h.get("_source");
            if (src == null) return;
            FeedbackDoc doc = new FeedbackDoc();
            if (src.has("feedbackId")) doc.setFeedbackId(src.get("feedbackId").asLong());
            if (src.has("patientId")) doc.setPatientId(src.get("patientId").asLong());
            if (src.has("patientName")) doc.setPatientName(src.get("patientName").asText());
            if (src.has("feedbackComment")) doc.setFeedbackComment(src.get("feedbackComment").asText());
            if (src.has("rating")) doc.setRating(src.get("rating").asInt());
            if (src.has("sentiment")) doc.setSentiment(src.get("sentiment").asText());
            if (src.has("doctorName")) doc.setDoctorName(src.get("doctorName").asText());
            if (src.has("specialization")) doc.setSpecialization(src.get("specialization").asText());
            doc.setSearchScore(h.has("_score") ? h.get("_score").asDouble() : 0.0);
            if (h.has("highlight")) {
                List<String> hl = new ArrayList<>();
                h.get("highlight").fields().forEachRemaining(f -> f.getValue().forEach(v -> hl.add(v.asText())));
                doc.setMatchHighlights(hl);
            }
            list.add(doc);
        });
        return list;
    }

    private List<DoctorDoc> parseClusterDoctorHits(com.fasterxml.jackson.databind.JsonNode root) {
        List<DoctorDoc> list = new ArrayList<>();
        root.get("hits").get("hits").forEach(h -> {
            var src = h.get("_source");
            if (src == null) return;
            DoctorDoc doc = new DoctorDoc();
            if (src.has("doctorId")) doc.setDoctorId(src.get("doctorId").asLong());
            if (src.has("fullName")) doc.setFullName(src.get("fullName").asText());
            if (src.has("experience")) doc.setExperience(src.get("experience").asInt());
            if (src.has("avgRating")) doc.setAvgRating(src.get("avgRating").asDouble());
            if (src.has("city")) doc.setCity(src.get("city").asText());
            if (src.has("specializations")) {
                List<String> specs = new ArrayList<>();
                src.get("specializations").forEach(s -> specs.add(s.asText()));
                doc.setSpecializations(specs);
            }
            list.add(doc);
        });
        return list;
    }

    private List<HospitalDoc> parseClusterHospitalHits(com.fasterxml.jackson.databind.JsonNode root) {
        List<HospitalDoc> list = new ArrayList<>();
        root.get("hits").get("hits").forEach(h -> {
            var src = h.get("_source");
            if (src == null) return;
            HospitalDoc doc = new HospitalDoc();
            if (src.has("hospitalId")) doc.setHospitalId(src.get("hospitalId").asLong());
            if (src.has("name")) doc.setName(src.get("name").asText());
            if (src.has("city")) doc.setCity(src.get("city").asText());
            if (src.has("area")) doc.setArea(src.get("area").asText());
            if (src.has("doctorCount")) doc.setDoctorCount(src.get("doctorCount").asInt());
            list.add(doc);
        });
        return list;
    }
}
