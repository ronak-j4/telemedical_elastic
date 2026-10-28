package com.hospital.dashboard.elastic.model;

public class ConsultationAnalyticsDoc {
    private Long consultationId;
    private Long patientId;
    private String patientName;
    private String patientCity;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private String date;
    private String status; // COMPLETED, SCHEDULED, CANCELLED
    private String consultationMode; // VIDEO, AUDIO, CHAT
    private double paymentAmount;
    private String paymentMethod; // UPI, CARD, NETBANKING, COD
    private Integer rating;

    public ConsultationAnalyticsDoc() {}

    public Long getConsultationId() { return consultationId; }
    public void setConsultationId(Long consultationId) { this.consultationId = consultationId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPatientCity() { return patientCity; }
    public void setPatientCity(String patientCity) { this.patientCity = patientCity; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDoctorSpecialization() { return doctorSpecialization; }
    public void setDoctorSpecialization(String doctorSpecialization) { this.doctorSpecialization = doctorSpecialization; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConsultationMode() { return consultationMode; }
    public void setConsultationMode(String consultationMode) { this.consultationMode = consultationMode; }
    public double getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(double paymentAmount) { this.paymentAmount = paymentAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
}
