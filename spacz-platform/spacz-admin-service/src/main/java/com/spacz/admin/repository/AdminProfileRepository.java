package com.spacz.admin.repository;

import com.spacz.admin.entity.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {

    Optional<AdminProfile> findByAdminUserId(Long adminUserId);
}
