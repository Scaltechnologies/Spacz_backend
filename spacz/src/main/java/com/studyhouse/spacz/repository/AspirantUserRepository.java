package com.studyhouse.spacz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.entity.AspirantUser;

@Repository
public interface AspirantUserRepository extends JpaRepository<AspirantUser, Long> {
}