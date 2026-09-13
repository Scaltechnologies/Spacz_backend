package com.studyhouse.spacz.service;

public interface UserService {
    
   	String generateOtp(String phoneNumber);

	void register(String phoneNumber, String otp);

	boolean authenticate(String phoneNumber, String otp);
}
