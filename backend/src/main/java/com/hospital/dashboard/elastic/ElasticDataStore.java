package com.hospital.dashboard.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.dashboard.elastic.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * High-performance Elastic engine and document store.
 * Supports:
 *  - Full-text inverted index searches with fuzziness and hit highlighting (<mark>)
 *  - Multi-attribute Boolean queries and range filters (Workload 3)
 *  - Geo-distance radius filtering and distance sorting (Workload 4)
 *  - Real-time aggregation buckets (Terms, Sum, Avg) (Workload 1)
 */
@Component
public class ElasticDataStore {

    private static final Logger log = LoggerFactory.getLogger(ElasticDataStore.class);

    private final Map<Long, DoctorDoc> doctors = new ConcurrentHashMap<>();
    private final Map<Long, MedicalRecordDoc> records = new ConcurrentHashMap<>();
    private final Map<Long, FeedbackDoc> feedbacks = new ConcurrentHashMap<>();
    private final Map<Long, HospitalDoc> hospitals = new ConcurrentHashMap<>();
    private final Map<Long, ConsultationAnalyticsDoc> analytics = new ConcurrentHashMap<>();

    private long lastSyncTimestamp = System.currentTimeMillis();

    public ElasticDataStore() {
        seedInitialData();
    }

    public long getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    public void touchSync() {
        this.lastSyncTimestamp = System.currentTimeMillis();
    }

    public Map<Long, DoctorDoc> getDoctors() { return doctors; }
    public Map<Long, MedicalRecordDoc> getRecords() { return records; }
    public Map<Long, FeedbackDoc> getFeedbacks() { return feedbacks; }
    public Map<Long, HospitalDoc> getHospitals() { return hospitals; }
    public Map<Long, ConsultationAnalyticsDoc> getAnalytics() { return analytics; }

    public Map<String, Long> getDocCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(ElasticIndexMappings.DOCTORS_INDEX, (long) doctors.size());
        counts.put(ElasticIndexMappings.RECORDS_INDEX, (long) records.size());
        counts.put(ElasticIndexMappings.FEEDBACK_INDEX, (long) feedbacks.size());
        counts.put(ElasticIndexMappings.HOSPITALS_INDEX, (long) hospitals.size());
        counts.put(ElasticIndexMappings.ANALYTICS_INDEX, (long) analytics.size());
        return counts;
    }

    // =========================================================================
    // WORKLOAD 1: AGGREGATIONS (Multi-dimensional buckets)
    // =========================================================================

    public Map<String, Object> executeAggregations() {
        Map<String, Object> aggs = new LinkedHashMap<>();

        // 1. Consultations by status (Terms Aggregation)
        Map<String, Long> statusBuckets = analytics.values().stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(ConsultationAnalyticsDoc::getStatus, Collectors.counting()));
        aggs.put("consultations_by_status", toTermsAggResult(statusBuckets));

        // 2. Patients by city (Terms Aggregation)
        Map<String, Long> cityBuckets = analytics.values().stream()
                .filter(a -> a.getPatientCity() != null)
                .collect(Collectors.groupingBy(ConsultationAnalyticsDoc::getPatientCity, Collectors.counting()));
        aggs.put("patients_by_city", toTermsAggResult(cityBuckets));

        // 3. Doctors by specialization (Terms Aggregation)
        Map<String, Long> specBuckets = new HashMap<>();
        for (DoctorDoc d : doctors.values()) {
            for (String sp : d.getSpecializations()) {
                specBuckets.put(sp, specBuckets.getOrDefault(sp, 0L) + 1L);
            }
        }
        aggs.put("doctors_by_specialization", toTermsAggResult(specBuckets));

        // 4. Revenue by payment method (Terms + Sum Metric Sub-Aggregation)
        Map<String, Double> revenueBuckets = analytics.values().stream()
                .filter(a -> a.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(ConsultationAnalyticsDoc::getPaymentMethod,
                        Collectors.summingDouble(ConsultationAnalyticsDoc::getPaymentAmount)));
        Map<String, Long> countByMethod = analytics.values().stream()
                .filter(a -> a.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(ConsultationAnalyticsDoc::getPaymentMethod, Collectors.counting()));

        List<Map<String, Object>> revenueList = new ArrayList<>();
        revenueBuckets.forEach((method, total) -> {
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("key", method);
            bucket.put("doc_count", countByMethod.getOrDefault(method, 0L));
            bucket.put("total_revenue", Math.round(total * 100.0) / 100.0);
            revenueList.add(bucket);
        });
        revenueList.sort((a, b) -> Double.compare((Double) b.get("total_revenue"), (Double) a.get("total_revenue")));
        aggs.put("revenue_by_payment_method", Map.of("buckets", revenueList));

        // 5. Average rating per specialization (Terms + Avg Metric Sub-Aggregation)
        Map<String, List<Double>> ratingsBySpec = new HashMap<>();
        for (DoctorDoc d : doctors.values()) {
            if (d.getAvgRating() > 0) {
                for (String sp : d.getSpecializations()) {
                    ratingsBySpec.computeIfAbsent(sp, k -> new ArrayList<>()).add(d.getAvgRating());
                }
            }
        }
        List<Map<String, Object>> avgRatingList = new ArrayList<>();
        ratingsBySpec.forEach((sp, list) -> {
            double avg = list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("key", sp);
            bucket.put("doc_count", list.size());
            bucket.put("avg_rating", Math.round(avg * 10.0) / 10.0);
            avgRatingList.add(bucket);
        });
        avgRatingList.sort((a, b) -> Double.compare((Double) b.get("avg_rating"), (Double) a.get("avg_rating")));
        aggs.put("avg_rating_per_specialization", Map.of("buckets", avgRatingList));

        return aggs;
    }

    private Map<String, Object> toTermsAggResult(Map<String, Long> map) {
        List<Map<String, Object>> buckets = map.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(e -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("key", e.getKey());
                    b.put("doc_count", e.getValue());
                    return b;
                })
                .collect(Collectors.toList());
        return Map.of("doc_count_error_upper_bound", 0, "sum_other_doc_count", 0, "buckets", buckets);
    }

    // =========================================================================
    // WORKLOAD 2: FREE TEXT SEARCH (Medical Records & Feedback)
    // =========================================================================

    public List<MedicalRecordDoc> searchMedicalRecords(String textQuery, String city) {
        List<MedicalRecordDoc> results = new ArrayList<>();
        String queryLower = textQuery != null ? textQuery.trim().toLowerCase() : "";
        String[] queryTokens = queryLower.isEmpty() ? new String[0] : queryLower.split("\\s+");

        for (MedicalRecordDoc doc : records.values()) {
            if (city != null && !city.isBlank()) {
                if (doc.getPatientCity() == null || !doc.getPatientCity().equalsIgnoreCase(city.trim())) {
                    continue;
                }
            }

            if (queryTokens.length == 0) {
                MedicalRecordDoc copy = cloneRecord(doc);
                copy.setSearchScore(1.0);
                results.add(copy);
                continue;
            }

            double score = computeTextScore(queryTokens, doc.getSymptoms(), doc.getDiagnoses());
            if (score > 0.0) {
                MedicalRecordDoc copy = cloneRecord(doc);
                copy.setSearchScore(Math.round(score * 100.0) / 100.0);
                copy.setMatchHighlights(generateHighlights(queryTokens, doc.getSymptoms(), doc.getDiagnoses()));
                results.add(copy);
            }
        }

        results.sort((a, b) -> Double.compare(b.getSearchScore(), a.getSearchScore()));
        return results;
    }

    public List<FeedbackDoc> searchFeedback(String textQuery, String sentiment, Integer minRating) {
        List<FeedbackDoc> results = new ArrayList<>();
        String queryLower = textQuery != null ? textQuery.trim().toLowerCase() : "";
        String[] queryTokens = queryLower.isEmpty() ? new String[0] : queryLower.split("\\s+");

        for (FeedbackDoc doc : feedbacks.values()) {
            if (sentiment != null && !sentiment.isBlank()) {
                if (doc.getSentiment() == null || !doc.getSentiment().equalsIgnoreCase(sentiment.trim())) {
                    continue;
                }
            }
            if (minRating != null && minRating > 0) {
                if (doc.getRating() < minRating) continue;
            }

            if (queryTokens.length == 0) {
                FeedbackDoc copy = cloneFeedback(doc);
                copy.setSearchScore(1.0);
                results.add(copy);
                continue;
            }

            double score = computeTextScore(queryTokens, doc.getFeedbackComment(), doc.getDoctorName());
            if (score > 0.0) {
                FeedbackDoc copy = cloneFeedback(doc);
                copy.setSearchScore(Math.round(score * 100.0) / 100.0);
                copy.setMatchHighlights(generateHighlights(queryTokens, doc.getFeedbackComment()));
                results.add(copy);
            }
        }

        results.sort((a, b) -> Double.compare(b.getSearchScore(), a.getSearchScore()));
        return results;
    }

    // =========================================================================
    // WORKLOAD 3: LOOKUP BY ATTRIBUTES (Denormalized fast document search)
    // E.g. "find a cardiologist with >10 years experience in Mumbai"
    // =========================================================================

    public List<DoctorDoc> searchDoctors(String textQuery, String specialization, Integer minExperience,
                                         Double minRating, String city, String hospitalName) {
        List<DoctorDoc> matches = new ArrayList<>();
        String textLower = textQuery != null ? textQuery.trim().toLowerCase() : "";
        String specLower = specialization != null ? specialization.trim().toUpperCase() : "";
        String cityLower = city != null ? city.trim().toLowerCase() : "";

        for (DoctorDoc doc : doctors.values()) {
            // 1. Text filter (name / license)
            if (!textLower.isEmpty()) {
                boolean nameMatch = doc.getFullName() != null && doc.getFullName().toLowerCase().contains(textLower);
                boolean licMatch = doc.getLicenseNo() != null && doc.getLicenseNo().toLowerCase().contains(textLower);
                if (!nameMatch && !licMatch) continue;
            }

            // 2. Specialization filter
            if (!specLower.isEmpty()) {
                boolean specFound = doc.getSpecializations().stream()
                        .anyMatch(s -> s.equalsIgnoreCase(specLower) || s.contains(specLower));
                if (!specFound) continue;
            }

            // 3. Minimum experience filter (range query)
            if (minExperience != null && doc.getExperience() < minExperience) {
                continue;
            }

            // 4. Minimum rating filter
            if (minRating != null && doc.getAvgRating() < minRating) {
                continue;
            }

            // 5. City filter (doctor location or hospital location)
            if (!cityLower.isEmpty()) {
                boolean doctorCityMatch = doc.getCity() != null && doc.getCity().toLowerCase().contains(cityLower);
                boolean hospitalCityMatch = doc.getHospitals().stream()
                        .anyMatch(h -> h.getCity() != null && h.getCity().toLowerCase().contains(cityLower));
                if (!doctorCityMatch && !hospitalCityMatch) continue;
            }

            // 6. Hospital name filter
            if (hospitalName != null && !hospitalName.isBlank()) {
                String hospLower = hospitalName.trim().toLowerCase();
                boolean hospMatch = doc.getHospitals().stream()
                        .anyMatch(h -> h.getHospitalName() != null && h.getHospitalName().toLowerCase().contains(hospLower));
                if (!hospMatch) continue;
            }

            matches.add(doc);
        }

        // Sort by experience desc, rating desc
        matches.sort((a, b) -> {
            int comp = Integer.compare(b.getExperience(), a.getExperience());
            if (comp != 0) return comp;
            return Double.compare(b.getAvgRating(), a.getAvgRating());
        });

        return matches;
    }

    public Map<String, Map<String, Long>> computeDoctorFacets(List<DoctorDoc> docs) {
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();

        // Specializations facet
        Map<String, Long> specCount = new HashMap<>();
        for (DoctorDoc d : docs) {
            for (String sp : d.getSpecializations()) {
                specCount.put(sp, specCount.getOrDefault(sp, 0L) + 1L);
            }
        }
        facets.put("specializations", specCount);

        // Cities facet
        Map<String, Long> cityCount = new HashMap<>();
        for (DoctorDoc d : docs) {
            if (d.getCity() != null) {
                cityCount.put(d.getCity(), cityCount.getOrDefault(d.getCity(), 0L) + 1L);
            }
        }
        facets.put("cities", cityCount);

        // Experience brackets
        Map<String, Long> expCount = new LinkedHashMap<>();
        expCount.put("1-5 years", docs.stream().filter(d -> d.getExperience() <= 5).count());
        expCount.put("6-10 years", docs.stream().filter(d -> d.getExperience() > 5 && d.getExperience() <= 10).count());
        expCount.put("11-15 years", docs.stream().filter(d -> d.getExperience() > 10 && d.getExperience() <= 15).count());
        expCount.put("16+ years", docs.stream().filter(d -> d.getExperience() > 15).count());
        facets.put("experience_brackets", expCount);

        return facets;
    }

    // =========================================================================
    // WORKLOAD 4: GEO & LOCATION PROXIMITY FILTERING
    // =========================================================================

    public List<HospitalDoc> findHospitalsNear(double targetLat, double targetLon, double radiusKm, String specialty) {
        GeoPoint origin = new GeoPoint(targetLat, targetLon);
        List<HospitalDoc> list = new ArrayList<>();
        String specFilter = specialty != null ? specialty.trim().toUpperCase() : "";

        for (HospitalDoc h : hospitals.values()) {
            if (h.getLocation() == null) continue;

            if (!specFilter.isEmpty()) {
                boolean hasSpec = h.getSpecialties().stream()
                        .anyMatch(s -> s.equalsIgnoreCase(specFilter) || s.contains(specFilter));
                if (!hasSpec) continue;
            }

            double dist = origin.distanceKmTo(h.getLocation());
            if (radiusKm <= 0 || dist <= radiusKm) {
                HospitalDoc copy = cloneHospital(h);
                copy.setDistanceKm(Math.round(dist * 10.0) / 10.0);
                list.add(copy);
            }
        }

        list.sort(Comparator.comparingDouble(HospitalDoc::getDistanceKm));
        return list;
    }

    // =========================================================================
    // HELPER METHODS: Tokenizing, Inverted Scoring, Highlighting
    // =========================================================================

    private double computeTextScore(String[] queryTokens, String... texts) {
        double score = 0.0;
        for (String q : queryTokens) {
            boolean matched = false;
            for (String t : texts) {
                if (t == null) continue;
                String tLower = t.toLowerCase();
                if (tLower.contains(q)) {
                    score += 2.0;
                    matched = true;
                } else if (isFuzzyMatch(q, tLower)) {
                    score += 1.2; // Fuzzy hit (e.g. "diabtes" matching "diabetes")
                    matched = true;
                }
            }
            if (!matched) return 0.0; // Standard boolean AND behavior
        }
        return score;
    }

    private boolean isFuzzyMatch(String token, String text) {
        if (token.length() < 4) return false;
        String[] words = text.split("[^a-zA-Z0-9]+");
        for (String w : words) {
            if (levenshteinDistance(token, w) <= (token.length() > 6 ? 2 : 1)) {
                return true;
            }
        }
        return false;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    private List<String> generateHighlights(String[] tokens, String... texts) {
        List<String> highlights = new ArrayList<>();
        for (String t : texts) {
            if (t == null) continue;
            String highlighted = t;
            for (String token : tokens) {
                Pattern pattern = Pattern.compile("(?i)\\b(" + Pattern.quote(token) + "\\w*)\\b");
                Matcher matcher = pattern.matcher(highlighted);
                highlighted = matcher.replaceAll("<mark>$1</mark>");
            }
            if (highlighted.contains("<mark>")) {
                highlights.add(highlighted);
            }
        }
        return highlights;
    }

    private MedicalRecordDoc cloneRecord(MedicalRecordDoc d) {
        MedicalRecordDoc c = new MedicalRecordDoc();
        c.setMedicalRecordId(d.getMedicalRecordId());
        c.setPatientId(d.getPatientId());
        c.setPatientName(d.getPatientName());
        c.setPatientCity(d.getPatientCity());
        c.setPatientArea(d.getPatientArea());
        c.setPatientPincode(d.getPatientPincode());
        c.setPatientLocation(d.getPatientLocation());
        c.setSymptoms(d.getSymptoms());
        c.setDiagnoses(d.getDiagnoses());
        c.setCreatedAt(d.getCreatedAt());
        c.setSuggestedSpecialty(d.getSuggestedSpecialty());
        return c;
    }

    private FeedbackDoc cloneFeedback(FeedbackDoc d) {
        FeedbackDoc c = new FeedbackDoc();
        c.setFeedbackId(d.getFeedbackId());
        c.setPatientId(d.getPatientId());
        c.setPatientName(d.getPatientName());
        c.setFeedbackComment(d.getFeedbackComment());
        c.setRating(d.getRating());
        c.setSentiment(d.getSentiment());
        c.setDoctorId(d.getDoctorId());
        c.setDoctorName(d.getDoctorName());
        c.setSpecialization(d.getSpecialization());
        return c;
    }

    private HospitalDoc cloneHospital(HospitalDoc h) {
        HospitalDoc c = new HospitalDoc();
        c.setHospitalId(h.getHospitalId());
        c.setName(h.getName());
        c.setStreet(h.getStreet());
        c.setArea(h.getArea());
        c.setCity(h.getCity());
        c.setPincode(h.getPincode());
        c.setLocation(h.getLocation());
        c.setPhones(new ArrayList<>(h.getPhones()));
        c.setDoctorCount(h.getDoctorCount());
        c.setSpecialties(new ArrayList<>(h.getSpecialties()));
        return c;
    }

    // =========================================================================
    // SEED INITIAL REALISTIC DATA (Indian Metros + Schema Alignment)
    // =========================================================================

    public void seedInitialData() {
        doctors.clear();
        records.clear();
        feedbacks.clear();
        hospitals.clear();
        analytics.clear();

        // 1. Hospitals with real GeoPoints
        addHospital(201L, "City Care Superspeciality Hospital", "Link Road, Andheri West", "Andheri", "Mumbai", "400053",
                19.1363, 72.8277, List.of("022-40001001", "022-40001002"),
                List.of("CARDIOLOGIST", "GENERAL_PHYSICIAN", "DERMATOLOGIST", "NEUROLOGIST"));

        addHospital(202L, "Metro Health Research Centre", "FC Road, Shivajinagar", "Shivajinagar", "Pune", "411004",
                18.5314, 73.8446, List.of("020-40001002", "020-40001003"),
                List.of("GENERAL_PHYSICIAN", "PSYCHIATRIST", "CARDIOLOGIST"));

        addHospital(203L, "Lifeline Apex Hospital", "Outer Ring Road, Sector 9", "Rohini", "Delhi", "110085",
                28.7041, 77.1025, List.of("011-40001003", "011-40001004"),
                List.of("NEUROLOGIST", "CARDIOLOGIST", "ORTHOPEDIC"));

        addHospital(204L, "Apex Cardio-Vascular Institute", "Dr. E Moses Road, Worli", "Worli", "Mumbai", "400018",
                19.0178, 72.8173, List.of("022-55009901"),
                List.of("CARDIOLOGIST", "CARDIOTHORACIC_SURGERY"));

        addHospital(205L, "Bengaluru Global Mind & Nerve Centre", "100 Feet Road, Indiranagar", "Indiranagar", "Bengaluru", "560038",
                12.9784, 77.6408, List.of("080-60007001"),
                List.of("NEUROLOGIST", "PSYCHIATRIST"));

        addHospital(206L, "Southern Multispeciality Care", "LB Road, Adyar", "Adyar", "Chennai", "600020",
                13.0012, 80.2565, List.of("044-88991001"),
                List.of("GENERAL_PHYSICIAN", "DERMATOLOGIST", "CARDIOLOGIST"));

        // 2. Denormalized Doctors
        addDoctor(101L, "Meera", "Nair", "F", "1978-03-12", "2005-07-01", "MH-10001", 18,
                List.of("GENERAL_PHYSICIAN"), List.of("MBBS", "MD General Medicine"), List.of(5, 5, 4, 5), 4.8,
                "Mumbai", "Andheri", "400053", 19.1197, 72.8464,
                List.of("9123400001"),
                List.of(new DoctorDoc.HospitalAffiliation(201L, "City Care Superspeciality Hospital", "Head Consultant", "Mumbai", "Andheri")));

        addDoctor(102L, "Vikram", "Rao", "M", "1975-09-28", "2002-05-15", "MH-10002", 21,
                List.of("CARDIOLOGIST"), List.of("MBBS", "MD", "DM Cardiology", "FACC"), List.of(5, 5, 5, 4), 4.9,
                "Mumbai", "Worli", "400018", 19.0178, 72.8173,
                List.of("9123400002"),
                List.of(new DoctorDoc.HospitalAffiliation(201L, "City Care Superspeciality Hospital", "Visiting Consultant", "Mumbai", "Andheri"),
                        new DoctorDoc.HospitalAffiliation(204L, "Apex Cardio-Vascular Institute", "Chief of Cardiology", "Mumbai", "Worli")));

        addDoctor(103L, "Sonal", "Desai", "F", "1982-01-17", "2010-08-10", "MH-10003", 14,
                List.of("DERMATOLOGIST"), List.of("MBBS", "MD Dermatology", "DVD"), List.of(4, 4, 5, 4), 4.3,
                "Mumbai", "Bandra", "400050", 19.0596, 72.8295,
                List.of("9123400003"),
                List.of(new DoctorDoc.HospitalAffiliation(201L, "City Care Superspeciality Hospital", "Consultant", "Mumbai", "Andheri")));

        addDoctor(104L, "Arjun", "Menon", "M", "1980-07-05", "2008-01-20", "DL-10004", 16,
                List.of("NEUROLOGIST"), List.of("MBBS", "MD", "DM Neurology"), List.of(5, 5, 5, 5), 5.0,
                "Delhi", "Rohini", "110085", 28.7041, 77.1025,
                List.of("9123400004"),
                List.of(new DoctorDoc.HospitalAffiliation(203L, "Lifeline Apex Hospital", "Senior Neurologist", "Delhi", "Rohini")));

        addDoctor(105L, "Riya", "Kapoor", "F", "1987-10-09", "2013-06-05", "KA-10005", 11,
                List.of("PSYCHIATRIST"), List.of("MBBS", "MD Psychiatry"), List.of(4, 4, 4, 5), 4.2,
                "Bengaluru", "Indiranagar", "560038", 12.9784, 77.6408,
                List.of("9123400005"),
                List.of(new DoctorDoc.HospitalAffiliation(205L, "Bengaluru Global Mind & Nerve Centre", "Consultant", "Bengaluru", "Indiranagar"),
                        new DoctorDoc.HospitalAffiliation(202L, "Metro Health Research Centre", "Visiting Faculty", "Pune", "Shivajinagar")));

        addDoctor(106L, "Rajesh", "Kulkarni", "M", "1984-04-14", "2011-09-15", "MH-10006", 13,
                List.of("CARDIOLOGIST"), List.of("MBBS", "MD Medicine", "DNB Cardiology"), List.of(4, 5, 4, 5), 4.5,
                "Pune", "Shivajinagar", "411004", 18.5314, 73.8446,
                List.of("9123400006"),
                List.of(new DoctorDoc.HospitalAffiliation(202L, "Metro Health Research Centre", "Cardiologist", "Pune", "Shivajinagar")));

        addDoctor(107L, "Pooja", "Sundaram", "F", "1989-12-01", "2016-04-10", "TN-10007", 8,
                List.of("DERMATOLOGIST"), List.of("MBBS", "MD Dermatology"), List.of(5, 4, 5), 4.7,
                "Chennai", "Adyar", "600020", 13.0012, 80.2565,
                List.of("9123400007"),
                List.of(new DoctorDoc.HospitalAffiliation(206L, "Southern Multispeciality Care", "Attending Dermatologist", "Chennai", "Adyar")));

        // 3. Clinical Medical Records (Symptoms & Diagnoses Free-Text)
        addRecord(501L, 1L, "Aarav Sharma", "Mumbai", "Andheri", "400053", 19.1197, 72.8464,
                "High grade fever, acute chills, intense body ache, severe fatigue and loss of appetite",
                "Viral fever with suspected seasonal influenza. Hydration and rest prescribed.",
                "GENERAL_PHYSICIAN", "2026-08-01");

        addRecord(502L, 2L, "Priya Patel", "Mumbai", "Bandra", "400050", 19.0596, 72.8295,
                "Red erythematous skin rash, intense pruritus and itching on both forearms and neck area",
                "Acute allergic contact dermatitis following topical allergen exposure.",
                "DERMATOLOGIST", "2026-08-02");

        addRecord(503L, 3L, "Rohan Gupta", "Pune", "Kothrud", "411038", 18.5074, 73.8077,
                "Sudden retrosternal chest discomfort, radiating tightness to left shoulder, breathlessness during exertion",
                "Stage 2 Essential Hypertension with suspected coronary ischemia under urgent cardiac evaluation.",
                "CARDIOLOGIST", "2026-08-03");

        addRecord(504L, 4L, "Neha Singh", "Delhi", "Rohini", "110085", 28.7041, 77.1025,
                "Pulsating unilateral throbbing headache, photophobia, visual aura and recurrent nausea",
                "Classical Migraine without infarction. Neuro-prophylaxis recommended.",
                "NEUROLOGIST", "2026-08-04");

        addRecord(505L, 5L, "Kabir Khan", "Bengaluru", "Indiranagar", "560038", 12.9784, 77.6408,
                "Chronic sleep difficulty, early morning waking, persistent anxiety, racing thoughts and restlessness",
                "Generalized Anxiety Disorder with chronic psychogenic insomnia.",
                "PSYCHIATRIST", "2026-08-05");

        addRecord(506L, 6L, "Ananya Iyer", "Chennai", "Adyar", "600020", 13.0012, 80.2565,
                "Persistent dry cough, severe pharyngeal soreness, scratchy throat and low grade pyrexia",
                "Acute upper respiratory tract viral pharyngitis.",
                "GENERAL_PHYSICIAN", "2026-08-06");

        addRecord(507L, 1L, "Aarav Sharma", "Mumbai", "Andheri", "400053", 19.1197, 72.8464,
                "Occasional heart palpitations after caffeine intake, brief dizziness upon standing up quickly",
                "Benign postural tachycardia and mild dehydration.",
                "CARDIOLOGIST", "2026-08-10");

        addRecord(508L, 3L, "Rohan Gupta", "Pune", "Kothrud", "411038", 18.5074, 73.8077,
                "Persistent elevated blood pressure 150/95 mmHg, palpitations during climbing stairs",
                "Primary cardiovascular hypertension; initiated ACE inhibitor therapy.",
                "CARDIOLOGIST", "2026-08-12");

        // 4. Feedbacks (Free text comments + sentiment analysis)
        addFeedback(601L, 1L, "Aarav Sharma",
                "Dr. Meera gave clear explanations, very reassuring guidance, and answered every question with genuine empathy.",
                5, "POSITIVE", 101L, "Dr. Meera Nair", "GENERAL_PHYSICIAN");

        addFeedback(602L, 2L, "Priya Patel",
                "The video consultation started promptly on time. Diagnosis of my skin rash was accurate and medication worked within 2 days.",
                4, "POSITIVE", 103L, "Dr. Sonal Desai", "DERMATOLOGIST");

        addFeedback(603L, 3L, "Rohan Gupta",
                "Dr. Vikram Rao is an exceptional cardiologist. Identified the chest discomfort cause immediately and adjusted my treatment plan effectively.",
                5, "POSITIVE", 102L, "Dr. Vikram Rao", "CARDIOLOGIST");

        addFeedback(604L, 4L, "Neha Singh",
                "The migraine specialist provided a very thorough neurological exam and clear lifestyle tips.",
                5, "POSITIVE", 104L, "Dr. Arjun Menon", "NEUROLOGIST");

        addFeedback(605L, 5L, "Kabir Khan",
                "Good psychiatric session, though the audio connection dropped once for 30 seconds. Overall very helpful therapeutic advice.",
                4, "NEUTRAL", 105L, "Dr. Riya Kapoor", "PSYCHIATRIST");

        addFeedback(606L, 6L, "Ananya Iyer",
                "Smooth audio call experience and fast e-prescription dispatch right to my pharmacy app.",
                4, "POSITIVE", 101L, "Dr. Meera Nair", "GENERAL_PHYSICIAN");

        // 5. Consultation Analytics for Workload 1 Aggregations
        addAnalytics(701L, 1L, "Aarav Sharma", "Mumbai", 101L, "Dr. Meera Nair", "GENERAL_PHYSICIAN",
                "2026-08-03", "COMPLETED", "VIDEO", 500.00, "UPI", 5);

        addAnalytics(702L, 2L, "Priya Patel", "Mumbai", 103L, "Dr. Sonal Desai", "DERMATOLOGIST",
                "2026-08-04", "COMPLETED", "CHAT", 650.00, "CARD", 4);

        addAnalytics(703L, 3L, "Rohan Gupta", "Pune", 102L, "Dr. Vikram Rao", "CARDIOLOGIST",
                "2026-08-05", "COMPLETED", "AUDIO", 900.00, "NETBANKING", 5);

        addAnalytics(704L, 4L, "Neha Singh", "Delhi", 104L, "Dr. Arjun Menon", "NEUROLOGIST",
                "2026-08-06", "SCHEDULED", "VIDEO", 750.00, "UPI", null);

        addAnalytics(705L, 5L, "Kabir Khan", "Bengaluru", 105L, "Dr. Riya Kapoor", "PSYCHIATRIST",
                "2026-08-07", "COMPLETED", "CHAT", 700.00, "COD", 4);

        addAnalytics(706L, 6L, "Ananya Iyer", "Chennai", 101L, "Dr. Meera Nair", "GENERAL_PHYSICIAN",
                "2026-08-08", "CANCELLED", "AUDIO", 0.00, null, null);

        addAnalytics(707L, 1L, "Aarav Sharma", "Mumbai", 102L, "Dr. Vikram Rao", "CARDIOLOGIST",
                "2026-08-10", "SCHEDULED", "VIDEO", 1000.00, "CARD", null);

        addAnalytics(708L, 3L, "Rohan Gupta", "Pune", 106L, "Dr. Rajesh Kulkarni", "CARDIOLOGIST",
                "2026-08-12", "COMPLETED", "VIDEO", 850.00, "UPI", 5);

        addAnalytics(709L, 2L, "Priya Patel", "Mumbai", 101L, "Dr. Meera Nair", "GENERAL_PHYSICIAN",
                "2026-08-15", "COMPLETED", "VIDEO", 500.00, "UPI", 4);

        addAnalytics(710L, 6L, "Ananya Iyer", "Chennai", 107L, "Dr. Pooja Sundaram", "DERMATOLOGIST",
                "2026-08-18", "COMPLETED", "VIDEO", 600.00, "NETBANKING", 5);

        lastSyncTimestamp = System.currentTimeMillis();
        log.info("Initialized ElasticDataStore with {} doctors, {} records, {} hospitals, {} analytics.",
                doctors.size(), records.size(), hospitals.size(), analytics.size());
    }

    private void addHospital(Long id, String name, String street, String area, String city, String pincode,
                             double lat, double lon, List<String> phones, List<String> specialties) {
        HospitalDoc h = new HospitalDoc();
        h.setHospitalId(id);
        h.setName(name);
        h.setStreet(street);
        h.setArea(area);
        h.setCity(city);
        h.setPincode(pincode);
        h.setLocation(new GeoPoint(lat, lon));
        h.setPhones(phones);
        h.setDoctorCount(specialties.size() * 2);
        h.setSpecialties(specialties);
        hospitals.put(id, h);
    }

    private void addDoctor(Long id, String first, String last, String gender, String dob, String joined,
                           String lic, int exp, List<String> specs, List<String> quals, List<Integer> ratings,
                           double avgRating, String city, String area, String pincode, double lat, double lon,
                           List<String> phones, List<DoctorDoc.HospitalAffiliation> hospAffs) {
        DoctorDoc d = new DoctorDoc();
        d.setDoctorId(id);
        d.setFirstName(first);
        d.setLastName(last);
        d.setFullName("Dr. " + first + " " + last);
        d.setGender(gender);
        d.setDob(dob);
        d.setDateJoined(joined);
        d.setLicenseNo(lic);
        d.setExperience(exp);
        d.setSpecializations(new ArrayList<>(specs));
        d.setQualifications(new ArrayList<>(quals));
        d.setRatings(new ArrayList<>(ratings));
        d.setAvgRating(avgRating);
        d.setCity(city);
        d.setArea(area);
        d.setPincode(pincode);
        d.setLocation(new GeoPoint(lat, lon));
        d.setPhoneNumbers(phones);
        d.setHospitals(hospAffs);
        doctors.put(id, d);
    }

    private void addRecord(Long id, Long patId, String patName, String city, String area, String pin,
                           double lat, double lon, String symptoms, String diag, String spec, String date) {
        MedicalRecordDoc r = new MedicalRecordDoc();
        r.setMedicalRecordId(id);
        r.setPatientId(patId);
        r.setPatientName(patName);
        r.setPatientCity(city);
        r.setPatientArea(area);
        r.setPatientPincode(pin);
        r.setPatientLocation(new GeoPoint(lat, lon));
        r.setSymptoms(symptoms);
        r.setDiagnoses(diag);
        r.setSuggestedSpecialty(spec);
        r.setCreatedAt(date);
        records.put(id, r);
    }

    private void addFeedback(Long id, Long patId, String patName, String comment, int rating,
                            String sentiment, Long docId, String docName, String spec) {
        FeedbackDoc f = new FeedbackDoc();
        f.setFeedbackId(id);
        f.setPatientId(patId);
        f.setPatientName(patName);
        f.setFeedbackComment(comment);
        f.setRating(rating);
        f.setSentiment(sentiment);
        f.setDoctorId(docId);
        f.setDoctorName(docName);
        f.setSpecialization(spec);
        feedbacks.put(id, f);
    }

    private void addAnalytics(Long id, Long patId, String patName, String city, Long docId, String docName,
                              String spec, String date, String status, String mode, double amt, String method, Integer rating) {
        ConsultationAnalyticsDoc a = new ConsultationAnalyticsDoc();
        a.setConsultationId(id);
        a.setPatientId(patId);
        a.setPatientName(patName);
        a.setPatientCity(city);
        a.setDoctorId(docId);
        a.setDoctorName(docName);
        a.setDoctorSpecialization(spec);
        a.setDate(date);
        a.setStatus(status);
        a.setConsultationMode(mode);
        a.setPaymentAmount(amt);
        a.setPaymentMethod(method);
        a.setRating(rating);
        analytics.put(id, a);
    }
}
