package com.studyhouse.spacz.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studyhouse.spacz.entity.UserLogin;

public interface UserRepository extends JpaRepository<UserLogin, Long> {

    // Custom method to find user by phone number
    Optional<UserLogin> findByPhoneNumber(String phoneNumber);
}
