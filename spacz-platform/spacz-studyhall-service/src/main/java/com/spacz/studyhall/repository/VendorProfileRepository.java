package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long>, JpaSpecificationExecutor<VendorProfile> {

    Optional<VendorProfile> findByVendorId(Long vendorId);

    /** SELECT ... FOR UPDATE: serializes a vendor's wallet withdrawals (balance check + reservation). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from VendorProfile v where v.vendorId = :vendorId")
    Optional<VendorProfile> findByVendorIdForUpdate(
            @org.springframework.data.repository.query.Param("vendorId") Long vendorId);

    Optional<VendorProfile> findByLegacyOwnerId(Long legacyOwnerId);

    boolean existsByVendorId(Long vendorId);

    long countByStatus(VendorStatus status);
}
