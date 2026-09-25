package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long>, JpaSpecificationExecutor<VendorProfile> {

    Optional<VendorProfile> findByVendorId(Long vendorId);

    Optional<VendorProfile> findByLegacyOwnerId(Long legacyOwnerId);

    boolean existsByVendorId(Long vendorId);

    long countByStatus(VendorStatus status);
}
