package com.spacz.studyhall.wallet.repository;

import com.spacz.studyhall.wallet.entity.Withdrawal;
import com.spacz.studyhall.wallet.entity.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long>, JpaSpecificationExecutor<Withdrawal> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Withdrawal w where w.id = :id")
    Optional<Withdrawal> findByIdForUpdate(@Param("id") Long id);

    Optional<Withdrawal> findByIdAndVendorId(Long id, Long vendorId);

    Optional<Withdrawal> findFirstByVendorIdAndStatusIn(Long vendorId, Collection<WithdrawalStatus> statuses);

    Page<Withdrawal> findByVendorIdOrderByRequestedAtDescIdDesc(Long vendorId, Pageable pageable);

    Page<Withdrawal> findByVendorIdAndStatusOrderByRequestedAtDescIdDesc(Long vendorId, WithdrawalStatus status,
                                                                         Pageable pageable);

    @Query("select coalesce(sum(w.amount), 0) from Withdrawal w where w.vendorId = :vendorId and w.status in :statuses")
    BigDecimal sumByVendorAndStatusIn(@Param("vendorId") Long vendorId,
                                      @Param("statuses") Collection<WithdrawalStatus> statuses);
}
