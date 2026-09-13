package com.studyhouse.spacz.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register/{phoneNumber}")
    public ResponseEntity<String> register(@PathVariable String phoneNumber) {
        String otp = userService.generateOtp(phoneNumber);
        userService.register(phoneNumber, otp);
        // Send OTP to the user's phone number via SMS
        return ResponseEntity.ok("Registration successful. OTP sent.");
    }

    @PostMapping("/login/{phoneNumber}/{otp}")
    public ResponseEntity<String> login(@PathVariable String phoneNumber, @PathVariable String otp) {
        if (userService.authenticate(phoneNumber, otp)) {
            return ResponseEntity.ok("Authentication successful.");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid OTP.");
    }
}
