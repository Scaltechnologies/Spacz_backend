package com.spacz.studyhall.wallet.repository;

import com.spacz.studyhall.wallet.entity.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {

    List<PayoutAccount> findByVendorIdAndActiveTrueOrderByDefaultAccountDescCreatedAtAsc(Long vendorId);

    Optional<PayoutAccount> findByIdAndVendorIdAndActiveTrue(Long id, Long vendorId);

    long countByVendorIdAndActiveTrue(Long vendorId);
}
