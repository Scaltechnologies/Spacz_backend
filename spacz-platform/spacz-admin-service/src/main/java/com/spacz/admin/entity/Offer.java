package com.spacz.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A promotional offer managed by admins. Shown to students while live, and applied to booking
 * prices by studyhall-service (best applicable offer wins).
 */
@Entity
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offer {

    public enum DiscountType { PERCENTAGE, FLAT }

    /** Booking plan the offer applies to. */
    public enum ApplicablePlan { ANY, DAILY, MONTHLY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Cap for PERCENTAGE discounts (null = no cap). */
    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    /** Minimum booking amount before discount (null = none). */
    @Column(name = "min_booking_amount", precision = 12, scale = 2)
    private BigDecimal minBookingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_plan", nullable = false, length = 10)
    private ApplicablePlan applicablePlan = ApplicablePlan.ANY;

    @Column(name = "first_booking_only", nullable = false)
    private boolean firstBookingOnly;

    /** Human-readable conditions shown to students. */
    @Column(length = 1000)
    private String terms;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean archived;

    @Column(nullable = false)
    private int priority;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Offer create(Long createdBy) {
        Offer offer = new Offer();
        offer.createdBy = createdBy;
        return offer;
    }

    /** Active, not archived and within its validity window. */
    public boolean isLive(Instant now) {
        return active && !archived && !now.isBefore(startAt) && now.isBefore(endAt);
    }
}
