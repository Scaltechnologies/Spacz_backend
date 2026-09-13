package com.studyhouse.spacz.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class AspirantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long aspirantUserId;  // Unique ID for the aspirant user

    private String name;          // Name of the aspirant
    private String phoneNumber;   // Phone number
    private String aadharNumber;  // Aadhar number (can be unique)
    private String email;         // Email address
    private String currentAddress;  // Current address
    private String permanentAddress; // Permanent address

    // One-to-Many relationship with Booking (one aspirant user can have many bookings)
    @OneToMany(mappedBy = "aspirantUser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Booking> bookings; // List of bookings made by the aspirant user

    // Getters and setters
    public Long getAspirantUserId() {
        return aspirantUserId;
    }

    public void setAspirantUserId(Long aspirantUserId) {
        this.aspirantUserId = aspirantUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAadharNumber() {
        return aadharNumber;
    }

    public void setAadharNumber(String aadharNumber) {
        this.aadharNumber = aadharNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrentAddress() {
        return currentAddress;
    }

    public void setCurrentAddress(String currentAddress) {
        this.currentAddress = currentAddress;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }
}
