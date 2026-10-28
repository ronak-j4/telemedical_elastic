package com.hospital.dashboard.elastic.model;

import java.util.ArrayList;
import java.util.List;

public class FeedbackDoc {
    private Long feedbackId;
    private Long patientId;
    private String patientName;
    private String feedbackComment;
    private int rating;
    private String sentiment; // POSITIVE, NEUTRAL, CRITICAL
    private Long doctorId;
    private String doctorName;
    private String specialization;
    private List<String> matchHighlights = new ArrayList<>();
    private double searchScore;

    public FeedbackDoc() {}

    public Long getFeedbackId() { return feedbackId; }
    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getFeedbackComment() { return feedbackComment; }
    public void setFeedbackComment(String feedbackComment) { this.feedbackComment = feedbackComment; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public List<String> getMatchHighlights() { return matchHighlights; }
    public void setMatchHighlights(List<String> matchHighlights) { this.matchHighlights = matchHighlights; }
    public double getSearchScore() { return searchScore; }
    public void setSearchScore(double searchScore) { this.searchScore = searchScore; }
}
