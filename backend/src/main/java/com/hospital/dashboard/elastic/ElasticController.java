package com.hospital.dashboard.elastic;

import com.hospital.dashboard.elastic.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing all four Elasticsearch workloads plus
 * status, sync and benchmark endpoints.
 *
 * Base path: /api/elastic
 */
@RestController
@RequestMapping("/api/elastic")
public class ElasticController {

    private final ElasticSearchService searchService;
    private final ElasticDataSyncService syncService;
    private final ElasticBenchmarkService benchmarkService;

    public ElasticController(ElasticSearchService searchService,
                              ElasticDataSyncService syncService,
                              ElasticBenchmarkService benchmarkService) {
        this.searchService = searchService;
        this.syncService = syncService;
        this.benchmarkService = benchmarkService;
    }

    // =========================================================================
    // CLUSTER STATUS
    // =========================================================================

    /** GET /api/elastic/status — cluster health + document counts */
    @GetMapping("/status")
    public ResponseEntity<ElasticClusterStatus> status() {
        return ResponseEntity.ok(searchService.getClusterStatus());
    }

    // =========================================================================
    // WORKLOAD 1 — AGGREGATIONS (Dashboard analytics without GROUP BY joins)
    // =========================================================================

    /** GET /api/elastic/aggregations — all dashboard aggregation buckets */
    @GetMapping("/aggregations")
    public ResponseEntity<ElasticSearchResult<Map<String, Object>>> aggregations() {
        return ResponseEntity.ok(searchService.runAggregations());
    }

    // =========================================================================
    // WORKLOAD 2 — FREE TEXT SEARCH
    // =========================================================================

    /**
     * GET /api/elastic/search/medical-records
     * Params: q (text), city
     */
    @GetMapping("/search/medical-records")
    public ResponseEntity<ElasticSearchResult<MedicalRecordDoc>> searchMedicalRecords(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(searchService.searchMedicalRecords(q, city));
    }

    /**
     * GET /api/elastic/search/feedback
     * Params: q (text), sentiment (POSITIVE|NEUTRAL|CRITICAL), minRating (1-5)
     */
    @GetMapping("/search/feedback")
    public ResponseEntity<ElasticSearchResult<FeedbackDoc>> searchFeedback(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) Integer minRating) {
        return ResponseEntity.ok(searchService.searchFeedback(q, sentiment, minRating));
    }

    // =========================================================================
    // WORKLOAD 3 — DOCTOR ATTRIBUTE LOOKUP (Denormalized)
    // =========================================================================

    /**
     * GET /api/elastic/search/doctors
     * Params: q, specialization, minExperience, minRating, city, hospital
     */
    @GetMapping("/search/doctors")
    public ResponseEntity<ElasticSearchResult<DoctorDoc>> searchDoctors(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String hospital) {
        return ResponseEntity.ok(searchService.searchDoctors(q, specialization, minExperience, minRating, city, hospital));
    }

    // =========================================================================
    // WORKLOAD 4 — GEO PROXIMITY SEARCH
    // =========================================================================

    /**
     * GET /api/elastic/search/hospitals/nearby
     * Params: lat, lon, radiusKm (default 10), specialty
     */
    @GetMapping("/search/hospitals/nearby")
    public ResponseEntity<ElasticSearchResult<HospitalDoc>> nearbyHospitals(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10.0") double radiusKm,
            @RequestParam(required = false) String specialty) {
        return ResponseEntity.ok(searchService.findNearbyHospitals(lat, lon, radiusKm, specialty));
    }

    // =========================================================================
    // SYNC
    // =========================================================================

    /** POST /api/elastic/sync — trigger full Oracle→Elastic sync */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        return ResponseEntity.ok(syncService.syncAll());
    }

    // =========================================================================
    // BENCHMARK
    // =========================================================================

    /** GET /api/elastic/benchmark — run all workload benchmarks */
    @GetMapping("/benchmark")
    public ResponseEntity<List<ElasticBenchmarkResult>> benchmark() {
        return ResponseEntity.ok(benchmarkService.runAllBenchmarks());
    }

    // =========================================================================
    // CONVENIENCE: all city names for geo dropdown
    // =========================================================================

    @GetMapping("/cities")
    public ResponseEntity<List<Map<String, Object>>> cities() {
        List<Map<String, Object>> cities = List.of(
            Map.of("name", "Mumbai",    "lat", 19.0760, "lon", 72.8777),
            Map.of("name", "Pune",      "lat", 18.5204, "lon", 73.8567),
            Map.of("name", "Delhi",     "lat", 28.6139, "lon", 77.2090),
            Map.of("name", "Bengaluru", "lat", 12.9716, "lon", 77.5946),
            Map.of("name", "Chennai",   "lat", 13.0827, "lon", 80.2707),
            Map.of("name", "Hyderabad", "lat", 17.3850, "lon", 78.4867)
        );
        return ResponseEntity.ok(cities);
    }

    /** GET /api/elastic/specializations — list of known specializations for filter dropdown */
    @GetMapping("/specializations")
    public ResponseEntity<List<String>> specializations() {
        return ResponseEntity.ok(List.of(
            "CARDIOLOGIST", "GENERAL_PHYSICIAN", "DERMATOLOGIST",
            "NEUROLOGIST", "PSYCHIATRIST", "ORTHOPEDIC",
            "CARDIOTHORACIC_SURGERY"
        ));
    }
}
