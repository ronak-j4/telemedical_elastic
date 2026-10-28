package com.hospital.dashboard.elastic.model;

import java.util.ArrayList;
import java.util.List;

public class HospitalDoc {
    private Long hospitalId;
    private String name;
    private String street;
    private String area;
    private String city;
    private String pincode;
    private GeoPoint location;
    private List<String> phones = new ArrayList<>();
    private int doctorCount;
    private List<String> specialties = new ArrayList<>();
    private double distanceKm; // computed dynamically relative to search origin

    public HospitalDoc() {}

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public List<String> getPhones() { return phones; }
    public void setPhones(List<String> phones) { this.phones = phones; }
    public int getDoctorCount() { return doctorCount; }
    public void setDoctorCount(int doctorCount) { this.doctorCount = doctorCount; }
    public List<String> getSpecialties() { return specialties; }
    public void setSpecialties(List<String> specialties) { this.specialties = specialties; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
}
