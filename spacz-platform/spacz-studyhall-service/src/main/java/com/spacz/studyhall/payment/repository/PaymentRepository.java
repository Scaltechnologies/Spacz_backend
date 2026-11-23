package com.spacz.studyhall.payment.repository;

import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Every state transition into/out of SUCCESS goes through this lock, so a webhook and a client
     * verify call racing on the same Payment serialize instead of confirming the booking twice. Lock
     * order everywhere: payment row, then (inside finalize) the seat row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** The open payment of a booking, if any (one at most: uq_payments_one_open_per_booking). */
    Optional<Payment> findFirstByBookingIdAndStatusIn(Long bookingId, Collection<PaymentStatus> statuses);

    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDescIdDesc(Long bookingId);

    List<Payment> findByBookingIdOrderByCreatedAtDescIdDesc(Long bookingId);

    boolean existsByBookingIdAndStatusAndIdNot(Long bookingId, PaymentStatus status, Long id);

    /** Sweep targets: sessions of one method still open past their TTL. */
    @Query("select p.id from Payment p where p.paymentMethod = :method and p.status in :statuses and p.expiresAt < :before")
    List<Long> findIdsByMethodAndStatusInAndExpiresAtBefore(@Param("method") PaymentMethod method,
                                                            @Param("statuses") Collection<PaymentStatus> statuses,
                                                            @Param("before") Instant before);

    @Query("select p.id from Payment p where p.status = :status order by p.id")
    List<Long> findIdsByStatus(@Param("status") PaymentStatus status);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Page<Payment> findByUserIdAndStatus(Long userId, PaymentStatus status, Pageable pageable);

    // ── Pay at Hall: vendor-facing lookups ────────────────────────────────

    List<Payment> findByVendorIdAndPaymentMethodAndStatusInOrderByCreatedAtAsc(
            Long vendorId, PaymentMethod paymentMethod, Collection<PaymentStatus> statuses);

    Page<Payment> findByVendorIdAndPaymentMethodOrderByCreatedAtDesc(
            Long vendorId, PaymentMethod paymentMethod, Pageable pageable);
}
