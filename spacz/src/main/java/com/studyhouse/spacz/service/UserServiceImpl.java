package com.studyhouse.spacz.service;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.UserLogin;
import com.studyhouse.spacz.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    public String generateOtp(String phoneNumber) {
        String lastFourDigits = phoneNumber.substring(phoneNumber.length() - 4);
        String firstDigit = String.valueOf(phoneNumber.charAt(0));
        return lastFourDigits + firstDigit;
    }

    public boolean authenticate(String phoneNumber, String otp) {
        Optional<UserLogin> userOpt = userRepository.findByPhoneNumber(phoneNumber);
        if (userOpt.isPresent()) {
            UserLogin user = userOpt.get();
            return user.getOtp().equals(otp);
        }
        return false;
    }

    public void register(String phoneNumber, String otp) {
        UserLogin user = new UserLogin();
        user.setPhoneNumber(phoneNumber);
        user.setOtp(otp);
        userRepository.save(user);
    }
}


