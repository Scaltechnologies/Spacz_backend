package com.spacz.studyhall.wallet.repository;

import com.spacz.studyhall.wallet.entity.WalletEntry;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {

    Optional<WalletEntry> findByPaymentIdAndType(Long paymentId, WalletEntryType type);

    Optional<WalletEntry> findByWithdrawalIdAndType(Long withdrawalId, WalletEntryType type);

    Page<WalletEntry> findByVendorIdOrderByCreatedAtDescIdDesc(Long vendorId, Pageable pageable);

    Page<WalletEntry> findByVendorIdAndTypeOrderByCreatedAtDescIdDesc(Long vendorId, WalletEntryType type,
                                                                      Pageable pageable);

    /** Entries that already count (available_at reached). */
    @Query("select coalesce(sum(e.amount), 0) from WalletEntry e where e.vendorId = :vendorId and e.availableAt <= :now")
    BigDecimal sumAvailable(@Param("vendorId") Long vendorId, @Param("now") Instant now);

    /** Entries still in the settlement hold. */
    @Query("select coalesce(sum(e.amount), 0) from WalletEntry e where e.vendorId = :vendorId and e.availableAt > :now")
    BigDecimal sumPending(@Param("vendorId") Long vendorId, @Param("now") Instant now);

    @Query("select coalesce(sum(e.amount), 0) from WalletEntry e where e.vendorId = :vendorId and e.type = :type")
    BigDecimal sumByType(@Param("vendorId") Long vendorId, @Param("type") WalletEntryType type);
}
