package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A seat reservation for an inclusive date range, priced per day or per month.
 * {@code userId} is the auth-service account ID. Overlaps are prevented by a row lock in the
 * service AND a PostgreSQL exclusion constraint.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 20, updatable = false)
    private String bookingReference;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false, updatable = false)
    private StudyHall studyHall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", updatable = false)
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private BookingPlan plan;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /** Price per day (DAILY) or per month (MONTHLY) at booking time. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalPrice;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "legacy_booking_id", unique = true, updatable = false)
    private Long legacyBookingId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static Booking hold(String reference, Long userId, Seat seat, Program program, BookingPlan plan,
                               LocalDate startDate, LocalDate endDate, BigDecimal unitPrice, Instant holdExpiresAt) {
        Booking booking = new Booking();
        booking.bookingReference = reference;
        booking.userId = userId;
        booking.studyHall = seat.getStudyHall();
        booking.seat = seat;
        booking.program = program;
        booking.plan = plan;
        booking.startDate = startDate;
        booking.endDate = endDate;
        booking.unitPrice = unitPrice;
        booking.totalPrice = unitPrice.multiply(BigDecimal.valueOf(units(plan, startDate, endDate)));
        booking.status = BookingStatus.PENDING;
        booking.holdExpiresAt = holdExpiresAt;
        return booking;
    }

    /** A confirmed booking migrated from the legacy system. */
    public static Booking imported(String reference, Long legacyBookingId, Long userId, Seat seat, LocalDate startDate,
                                   LocalDate endDate, BigDecimal unitPrice, Instant now) {
        Booking booking = hold(reference, userId, seat, null, BookingPlan.DAILY, startDate, endDate, unitPrice, null);
        booking.legacyBookingId = legacyBookingId;
        booking.status = BookingStatus.CONFIRMED;
        booking.confirmedAt = now;
        return booking;
    }

    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Days (DAILY) or whole months (MONTHLY) covered by the range. */
    public static long units(BookingPlan plan, LocalDate startDate, LocalDate endDate) {
        return plan == BookingPlan.MONTHLY
                ? ChronoUnit.MONTHS.between(startDate, endDate.plusDays(1))
                : daysBetween(startDate, endDate);
    }

    public long units() {
        return units(plan, startDate, endDate);
    }

    public long days() {
        return daysBetween(startDate, endDate);
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.PENDING && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }

    public void confirm(Instant now) {
        if (status != BookingStatus.PENDING) {
            throw new BusinessRuleException("INVALID_BOOKING_STATUS",
                    "Only PENDING bookings can be confirmed (current: " + status + ")");
        }
        if (isHoldExpired(now)) {
            throw new BusinessRuleException("BOOKING_HOLD_EXPIRED", "The seat hold has expired. Please book again.");
        }
        status = BookingStatus.CONFIRMED;
        confirmedAt = now;
        holdExpiresAt = null;
    }

    public void cancel(String reason, Instant now, LocalDate today) {
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("INVALID_BOOKING_STATUS", "Booking cannot be cancelled in status " + status);
        }
        if (status == BookingStatus.CONFIRMED && !startDate.isAfter(today)) {
            throw new BusinessRuleException("BOOKING_ALREADY_STARTED",
                    "A booking cannot be cancelled on or after its start date");
        }
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        cancellationReason = reason;
        holdExpiresAt = null;
    }

    public void expire() {
        if (status == BookingStatus.PENDING) {
            status = BookingStatus.EXPIRED;
        }
    }

    public void complete() {
        if (status == BookingStatus.CONFIRMED) {
            status = BookingStatus.COMPLETED;
        }
    }
}
