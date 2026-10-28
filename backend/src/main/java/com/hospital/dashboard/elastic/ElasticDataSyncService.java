package com.hospital.dashboard.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dashboard.elastic.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ElasticDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(ElasticDataSyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ElasticDataStore dataStore;
    private final ElasticsearchClientService clientService;
    private final ObjectMapper objectMapper;
    private final boolean autoSync;

    public ElasticDataSyncService(
            JdbcTemplate jdbcTemplate,
            ElasticDataStore dataStore,
            ElasticsearchClientService clientService,
            ObjectMapper objectMapper,
            @Value("${elasticsearch.auto-sync-on-boot:true}") boolean autoSync) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataStore = dataStore;
        this.clientService = clientService;
        this.objectMapper = objectMapper;
        this.autoSync = autoSync;
    }

    @PostConstruct
    public void init() {
        if (autoSync) {
            new Thread(() -> {
                try {
                    Thread.sleep(1500); // Give datasource and cluster a second to settle
                    syncAll();
                } catch (Exception e) {
                    log.warn("Auto-sync background job encountered an issue: {}", e.getMessage());
                }
            }, "elastic-sync-thread").start();
        }
    }

    /**
     * Performs a full synchronization across Oracle (if online), the in-memory engine,
     * and external Elasticsearch cluster (if reachable).
     */
    public Map<String, Object> syncAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        boolean clusterAvailable = clientService.isClusterReachable();
        result.put("clusterAvailable", clusterAvailable);
        result.put("clusterUri", clientService.getClusterUri());

        // Step 1: If live Elasticsearch cluster is present, ensure indices and mappings exist
        if (clusterAvailable) {
            try {
                clientService.createIndexWithMapping(ElasticIndexMappings.DOCTORS_INDEX, ElasticIndexMappings.DOCTORS_MAPPING);
                clientService.createIndexWithMapping(ElasticIndexMappings.RECORDS_INDEX, ElasticIndexMappings.RECORDS_MAPPING);
                clientService.createIndexWithMapping(ElasticIndexMappings.FEEDBACK_INDEX, ElasticIndexMappings.FEEDBACK_MAPPING);
                clientService.createIndexWithMapping(ElasticIndexMappings.HOSPITALS_INDEX, ElasticIndexMappings.HOSPITALS_MAPPING);
                clientService.createIndexWithMapping(ElasticIndexMappings.ANALYTICS_INDEX, ElasticIndexMappings.ANALYTICS_MAPPING);
            } catch (Exception e) {
                log.warn("Could not ensure indices on Elasticsearch: {}", e.getMessage());
            }
        }

        // Step 2: Try pulling live rows from Oracle database
        boolean oracleSynced = syncFromOracleDatabase();
        result.put("oracleDatabaseConnected", oracleSynced);

        // Step 3: If cluster is reachable, bulk index current dataStore documents into Elasticsearch
        int indexedToClusterCount = 0;
        if (clusterAvailable) {
            indexedToClusterCount = pushDataStoreToElasticCluster();
        }
        result.put("documentsIndexedToCluster", indexedToClusterCount);

        dataStore.touchSync();
        result.put("indexedCounts", dataStore.getDocCounts());
        result.put("syncDurationMs", System.currentTimeMillis() - startTime);
        result.put("mode", clusterAvailable ? "LIVE_ELASTICSEARCH" : "STANDALONE_ELASTIC_ENGINE");

        log.info("Elasticsearch synchronization completed in {} ms. Mode: {}",
                result.get("syncDurationMs"), result.get("mode"));
        return result;
    }

    private boolean syncFromOracleDatabase() {
        try {
            // Test Oracle connection with dual query
            jdbcTemplate.queryForObject("SELECT 1 FROM dual", Integer.class);
            log.info("Oracle database is reachable. Synchronizing live database records into Elastic...");

            // Sync Doctors + Specializations + Qualifications + Ratings + Hospital joins
            syncDoctorsFromOracle();

            // Sync Hospitals
            syncHospitalsFromOracle();

            // Sync Medical Records
            syncRecordsFromOracle();

            // Sync Feedback
            syncFeedbackFromOracle();

            // Sync Consultations Analytics
            syncAnalyticsFromOracle();

            return true;
        } catch (Exception e) {
            log.info("Oracle DB is currently unavailable or unseeded ({}). Retaining robust preloaded Elastic dataset.", e.getMessage());
            return false;
        }
    }

    private void syncDoctorsFromOracle() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM doctor");
            for (Map<String, Object> r : rows) {
                Long doctorId = ((Number) r.get("doctor_id")).longValue();
                DoctorDoc doc = dataStore.getDoctors().computeIfAbsent(doctorId, id -> new DoctorDoc());
                doc.setDoctorId(doctorId);
                doc.setFirstName((String) r.get("first_name"));
                doc.setLastName((String) r.get("last_name"));
                doc.setFullName("Dr. " + doc.getFirstName() + " " + doc.getLastName());
                doc.setGender(String.valueOf(r.get("gender")));
                if (r.get("license_no") != null) doc.setLicenseNo((String) r.get("license_no"));
                if (r.get("experience") != null) doc.setExperience(((Number) r.get("experience")).intValue());

                // Fetch specs
                List<String> specs = jdbcTemplate.query(
                        "SELECT specialization FROM doctor_specialization WHERE doctor_id = ?",
                        (rs, i) -> rs.getString("specialization"), doctorId);
                if (!specs.isEmpty()) doc.setSpecializations(specs);

                // Fetch qualifications
                List<String> quals = jdbcTemplate.query(
                        "SELECT qualification FROM doctor_qualification WHERE doctor_id = ?",
                        (rs, i) -> rs.getString("qualification"), doctorId);
                if (!quals.isEmpty()) doc.setQualifications(quals);

                // Fetch ratings
                List<Integer> ratings = jdbcTemplate.query(
                        "SELECT rating FROM doctor_rating WHERE doctor_id = ?",
                        (rs, i) -> rs.getInt("rating"), doctorId);
                if (!ratings.isEmpty()) {
                    doc.setRatings(ratings);
                    double avg = ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    doc.setAvgRating(Math.round(avg * 10.0) / 10.0);
                }

                // Fetch phone numbers
                List<String> phones = jdbcTemplate.query(
                        "SELECT phone_no FROM doctor_phone WHERE doctor_id = ?",
                        (rs, i) -> rs.getString("phone_no"), doctorId);
                if (!phones.isEmpty()) doc.setPhoneNumbers(phones);
            }
        } catch (Exception e) {
            log.warn("Failed fetching doctors from Oracle: {}", e.getMessage());
        }
    }

    private void syncHospitalsFromOracle() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM hospital");
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("hospital_id")).longValue();
                HospitalDoc h = dataStore.getHospitals().computeIfAbsent(id, k -> new HospitalDoc());
                h.setHospitalId(id);
                h.setName((String) r.get("name"));
                h.setStreet((String) r.get("street"));
                h.setArea((String) r.get("area"));
                h.setCity((String) r.get("city"));
                h.setPincode((String) r.get("pincode"));
                if (h.getLocation() == null) {
                    h.setLocation(assignCityCoordinates(h.getCity()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed fetching hospitals from Oracle: {}", e.getMessage());
        }
    }

    private void syncRecordsFromOracle() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT mr.medical_record_id, mr.patient_id, p.first_name, p.last_name, p.city, p.area, p.pincode, " +
                    "mr.symptoms, mr.diagnoses FROM medical_record mr LEFT JOIN patient p ON mr.patient_id = p.patient_id");
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("medical_record_id")).longValue();
                MedicalRecordDoc doc = dataStore.getRecords().computeIfAbsent(id, k -> new MedicalRecordDoc());
                doc.setMedicalRecordId(id);
                doc.setPatientId(((Number) r.get("patient_id")).longValue());
                doc.setPatientName(r.get("first_name") + " " + r.get("last_name"));
                doc.setPatientCity((String) r.get("city"));
                doc.setPatientArea((String) r.get("area"));
                doc.setPatientPincode((String) r.get("pincode"));
                doc.setSymptoms((String) r.get("symptoms"));
                doc.setDiagnoses((String) r.get("diagnoses"));
                doc.setPatientLocation(assignCityCoordinates(doc.getPatientCity()));
            }
        } catch (Exception e) {
            log.warn("Failed fetching medical records from Oracle: {}", e.getMessage());
        }
    }

    private void syncFeedbackFromOracle() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT f.feedback_id, f.patient_id, p.first_name, p.last_name, f.feedback_comment, f.rating " +
                    "FROM feedback f LEFT JOIN patient p ON f.patient_id = p.patient_id");
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("feedback_id")).longValue();
                FeedbackDoc f = dataStore.getFeedbacks().computeIfAbsent(id, k -> new FeedbackDoc());
                f.setFeedbackId(id);
                f.setPatientId(((Number) r.get("patient_id")).longValue());
                f.setPatientName(r.get("first_name") + " " + r.get("last_name"));
                f.setFeedbackComment((String) r.get("feedback_comment"));
                if (r.get("rating") != null) {
                    int rating = ((Number) r.get("rating")).intValue();
                    f.setRating(rating);
                    f.setSentiment(rating >= 4 ? "POSITIVE" : (rating == 3 ? "NEUTRAL" : "CRITICAL"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed fetching feedbacks from Oracle: {}", e.getMessage());
        }
    }

    private void syncAnalyticsFromOracle() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT c.consultation_id, c.patient_id, p.first_name || ' ' || p.last_name AS patient_name, p.city AS patient_city, " +
                    "c.doctor_id, d.first_name || ' ' || d.last_name AS doctor_name, c.status, c.consultation_mode, " +
                    "py.payment_amount, py.paid_by " +
                    "FROM consultation c " +
                    "LEFT JOIN patient p ON c.patient_id = p.patient_id " +
                    "LEFT JOIN doctor d ON c.doctor_id = d.doctor_id " +
                    "LEFT JOIN payment py ON c.consultation_id = py.consultation_id");
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("consultation_id")).longValue();
                ConsultationAnalyticsDoc a = dataStore.getAnalytics().computeIfAbsent(id, k -> new ConsultationAnalyticsDoc());
                a.setConsultationId(id);
                if (r.get("patient_id") != null) a.setPatientId(((Number) r.get("patient_id")).longValue());
                a.setPatientName((String) r.get("patient_name"));
                a.setPatientCity((String) r.get("patient_city"));
                if (r.get("doctor_id") != null) a.setDoctorId(((Number) r.get("doctor_id")).longValue());
                a.setDoctorName((String) r.get("doctor_name"));
                a.setStatus((String) r.get("status"));
                a.setConsultationMode((String) r.get("consultation_mode"));
                if (r.get("payment_amount") != null) a.setPaymentAmount(((Number) r.get("payment_amount")).doubleValue());
                a.setPaymentMethod((String) r.get("paid_by"));
            }
        } catch (Exception e) {
            log.warn("Failed fetching consultation analytics from Oracle: {}", e.getMessage());
        }
    }

    private int pushDataStoreToElasticCluster() {
        int count = 0;
        try {
            StringBuilder bulk = new StringBuilder();

            for (DoctorDoc d : dataStore.getDoctors().values()) {
                bulk.append(String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", ElasticIndexMappings.DOCTORS_INDEX, d.getDoctorId()));
                bulk.append(objectMapper.writeValueAsString(d)).append("\n");
                count++;
            }
            for (MedicalRecordDoc r : dataStore.getRecords().values()) {
                bulk.append(String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", ElasticIndexMappings.RECORDS_INDEX, r.getMedicalRecordId()));
                bulk.append(objectMapper.writeValueAsString(r)).append("\n");
                count++;
            }
            for (FeedbackDoc f : dataStore.getFeedbacks().values()) {
                bulk.append(String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", ElasticIndexMappings.FEEDBACK_INDEX, f.getFeedbackId()));
                bulk.append(objectMapper.writeValueAsString(f)).append("\n");
                count++;
            }
            for (HospitalDoc h : dataStore.getHospitals().values()) {
                bulk.append(String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", ElasticIndexMappings.HOSPITALS_INDEX, h.getHospitalId()));
                bulk.append(objectMapper.writeValueAsString(h)).append("\n");
                count++;
            }
            for (ConsultationAnalyticsDoc a : dataStore.getAnalytics().values()) {
                bulk.append(String.format("{\"index\":{\"_index\":\"%s\",\"_id\":\"%d\"}}\n", ElasticIndexMappings.ANALYTICS_INDEX, a.getConsultationId()));
                bulk.append(objectMapper.writeValueAsString(a)).append("\n");
                count++;
            }

            if (bulk.length() > 0) {
                clientService.bulkIndex(bulk.toString());
            }
        } catch (Exception e) {
            log.warn("Error during bulk indexing to cluster: {}", e.getMessage());
        }
        return count;
    }

    private GeoPoint assignCityCoordinates(String city) {
        if (city == null) return new GeoPoint(19.0760, 72.8777);
        return switch (city.toLowerCase().trim()) {
            case "pune" -> new GeoPoint(18.5204, 73.8567);
            case "delhi", "new delhi" -> new GeoPoint(28.6139, 77.2090);
            case "bengaluru", "bangalore" -> new GeoPoint(12.9716, 77.5946);
            case "chennai" -> new GeoPoint(13.0827, 80.2707);
            case "hyderabad" -> new GeoPoint(17.3850, 78.4867);
            case "mumbai" -> new GeoPoint(19.0760, 72.8777);
            default -> new GeoPoint(19.0760, 72.8777);
        };
    }
}
