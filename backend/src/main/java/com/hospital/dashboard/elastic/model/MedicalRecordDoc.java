package com.hospital.dashboard.elastic.model;

import java.util.ArrayList;
import java.util.List;

public class MedicalRecordDoc {
    private Long medicalRecordId;
    private Long patientId;
    private String patientName;
    private String patientCity;
    private String patientArea;
    private String patientPincode;
    private GeoPoint patientLocation;
    private String symptoms;
    private String diagnoses;
    private String createdAt;
    private String suggestedSpecialty;
    private List<String> matchHighlights = new ArrayList<>();
    private double searchScore;

    public MedicalRecordDoc() {}

    public Long getMedicalRecordId() { return medicalRecordId; }
    public void setMedicalRecordId(Long medicalRecordId) { this.medicalRecordId = medicalRecordId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPatientCity() { return patientCity; }
    public void setPatientCity(String patientCity) { this.patientCity = patientCity; }
    public String getPatientArea() { return patientArea; }
    public void setPatientArea(String patientArea) { this.patientArea = patientArea; }
    public String getPatientPincode() { return patientPincode; }
    public void setPatientPincode(String patientPincode) { this.patientPincode = patientPincode; }
    public GeoPoint getPatientLocation() { return patientLocation; }
    public void setPatientLocation(GeoPoint patientLocation) { this.patientLocation = patientLocation; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getDiagnoses() { return diagnoses; }
    public void setDiagnoses(String diagnoses) { this.diagnoses = diagnoses; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getSuggestedSpecialty() { return suggestedSpecialty; }
    public void setSuggestedSpecialty(String suggestedSpecialty) { this.suggestedSpecialty = suggestedSpecialty; }
    public List<String> getMatchHighlights() { return matchHighlights; }
    public void setMatchHighlights(List<String> matchHighlights) { this.matchHighlights = matchHighlights; }
    public double getSearchScore() { return searchScore; }
    public void setSearchScore(double searchScore) { this.searchScore = searchScore; }
}
