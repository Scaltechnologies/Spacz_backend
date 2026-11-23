package com.spacz.studyhall.payment.repository;

import com.spacz.studyhall.payment.entity.PayAtHallVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PayAtHallVerificationRepository extends JpaRepository<PayAtHallVerification, Long> {

    Optional<PayAtHallVerification> findByPaymentId(Long paymentId);

    Optional<PayAtHallVerification> findByQrTokenHash(String qrTokenHash);

    // Locked together with the parent Payment (findByIdForUpdate, always taken first) during
    // verify/confirm, so a QR scan and a code entry racing on the same session serialize instead of
    // double-counting attempts or letting two vendor devices both "win" a confirmation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PayAtHallVerification v where v.paymentId = :paymentId")
    Optional<PayAtHallVerification> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);
}
