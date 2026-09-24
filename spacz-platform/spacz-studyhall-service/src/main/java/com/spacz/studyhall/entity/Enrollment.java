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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A student's membership of a study hall: who studies here, for which exam, on which seat, until when.
 * It is the vendor's "students" list. Either a SPACZ account ({@code userId}) or a walk-in guest
 * the vendor added (guest name/phone/email, vendor-owned data).
 */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false, updatable = false)
    private StudyHall studyHall;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "guest_name", length = 120)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "guest_email", length = 254)
    private String guestEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingPlan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private EnrollmentSource source;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static Enrollment start(StudyHall hall, Long userId, EnrollmentSource source, BookingPlan plan,
                                   LocalDate startDate, LocalDate endDate) {
        Enrollment enrollment = new Enrollment();
        enrollment.studyHall = hall;
        enrollment.userId = userId;
        enrollment.source = source;
        enrollment.plan = plan;
        enrollment.startDate = startDate;
        enrollment.endDate = endDate;
        enrollment.status = EnrollmentStatus.ACTIVE;
        return enrollment;
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    /** The seat is held by this enrollment (vendor-added students) rather than by a booking. */
    public boolean holdsSeat() {
        return source == EnrollmentSource.WALK_IN && seat != null;
    }

    public void extendTo(LocalDate newEndDate) {
        if (endDate != null && (newEndDate == null || newEndDate.isAfter(endDate))) {
            endDate = newEndDate;
        }
    }

    public void finish(EnrollmentStatus target) {
        if (!isActive()) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION", "Enrollment is already " + status);
        }
        if (target == EnrollmentStatus.ACTIVE) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION", "Enrollment is already ACTIVE");
        }
        status = target;
    }
}
