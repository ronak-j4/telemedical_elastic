package com.hospital.dashboard.elastic.model;

import java.util.ArrayList;
import java.util.List;

public class DoctorDoc {
    private Long doctorId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String gender;
    private String dob;
    private String dateJoined;
    private String licenseNo;
    private int experience; // in years
    private List<String> specializations = new ArrayList<>();
    private List<String> qualifications = new ArrayList<>();
    private List<Integer> ratings = new ArrayList<>();
    private double avgRating;
    private List<HospitalAffiliation> hospitals = new ArrayList<>();
    private String city;
    private String area;
    private String pincode;
    private GeoPoint location;
    private List<String> phoneNumbers = new ArrayList<>();

    public static class HospitalAffiliation {
        private Long hospitalId;
        private String hospitalName;
        private String role;
        private String city;
        private String area;

        public HospitalAffiliation() {}

        public HospitalAffiliation(Long hospitalId, String hospitalName, String role, String city, String area) {
            this.hospitalId = hospitalId;
            this.hospitalName = hospitalName;
            this.role = role;
            this.city = city;
            this.area = area;
        }

        public Long getHospitalId() { return hospitalId; }
        public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
        public String getHospitalName() { return hospitalName; }
        public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getArea() { return area; }
        public void setArea(String area) { this.area = area; }
    }

    public DoctorDoc() {}

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getDateJoined() { return dateJoined; }
    public void setDateJoined(String dateJoined) { this.dateJoined = dateJoined; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = experience; }
    public List<String> getSpecializations() { return specializations; }
    public void setSpecializations(List<String> specializations) { this.specializations = specializations; }
    public List<String> getQualifications() { return qualifications; }
    public void setQualifications(List<String> qualifications) { this.qualifications = qualifications; }
    public List<Integer> getRatings() { return ratings; }
    public void setRatings(List<Integer> ratings) { this.ratings = ratings; }
    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
    public List<HospitalAffiliation> getHospitals() { return hospitals; }
    public void setHospitals(List<HospitalAffiliation> hospitals) { this.hospitals = hospitals; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public List<String> getPhoneNumbers() { return phoneNumbers; }
    public void setPhoneNumbers(List<String> phoneNumbers) { this.phoneNumbers = phoneNumbers; }
}
