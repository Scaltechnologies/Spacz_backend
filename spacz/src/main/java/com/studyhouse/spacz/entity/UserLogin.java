package com.studyhouse.spacz.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class UserLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loginId;  // Unique auto-generated ID
    private String phoneNumber;  // User's phone number
    private String otp;
    private boolean isOwnerRegistered;  // Whether the user is an owner and is registered

    // Getters and setters
    public Long getLoginId() {
        return loginId;
    }

    public void setLoginId(Long loginId) {
        this.loginId = loginId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isOwnerRegistered() {
        return isOwnerRegistered;
    }

    public void setOwnerRegistered(boolean ownerRegistered) {
        isOwnerRegistered = ownerRegistered;
    }

	public String getOtp() {
		return otp;
	}

	public void setOtp(String otp) {
		this.otp = otp;
	}
    
}
