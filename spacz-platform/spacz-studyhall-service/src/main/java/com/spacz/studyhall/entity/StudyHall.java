package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A study hall (one branch of a vendor). Legacy: the {@code property} table.
 * Prices here are defaults; a block or a seat can override them.
 */
@Entity
@Table(name = "study_halls")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyHall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_profile_id", nullable = false, updatable = false)
    private VendorProfile vendor;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 10)
    private String pincode;

    private Double latitude;

    private Double longitude;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "price_per_day", precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(name = "price_per_month", precision = 10, scale = 2)
    private BigDecimal pricePerMonth;

    @Column(length = 4000)
    private String rules;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyHallStatus status;

    @Setter(AccessLevel.NONE)
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Setter(AccessLevel.NONE)
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Legacy {@code property.property_id}, set by the MySQL migration. */
    @Column(name = "legacy_property_id", unique = true)
    private Long legacyPropertyId;

    @BatchSize(size = 50)
    @OrderBy("displayOrder ASC, id ASC")
    @OneToMany(mappedBy = "studyHall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudyHallImage> images = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "studyHall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OperatingHours> operatingHours = new ArrayList<>();

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "study_hall_amenities",
            joinColumns = @JoinColumn(name = "study_hall_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "study_hall_programs",
            joinColumns = @JoinColumn(name = "study_hall_id"),
            inverseJoinColumns = @JoinColumn(name = "program_id"))
    private Set<Program> programs = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @OrderBy("displayOrder ASC, id ASC")
    @OneToMany(mappedBy = "studyHall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Block> blocks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static StudyHall draft(VendorProfile vendor) {
        StudyHall hall = new StudyHall();
        hall.vendor = vendor;
        hall.status = StudyHallStatus.DRAFT;
        return hall;
    }

    /** Listed in search and bookable. */
    public boolean isBookable() {
        return status == StudyHallStatus.ACTIVE && vendor.isOperational();
    }

    public boolean isOwnedBy(Long vendorId) {
        return vendor.getVendorId().equals(vendorId);
    }

    public void submitForApproval(boolean approvalRequired, Instant now) {
        require(EnumSet.of(StudyHallStatus.DRAFT, StudyHallStatus.REJECTED), "submit");
        submittedAt = now;
        statusReason = null;
        status = StudyHallStatus.PENDING_APPROVAL;
        if (!approvalRequired) {
            approve(now);
        }
    }

    /** Approved halls go live as soon as their vendor is approved. */
    public void activateIfApproved() {
        if (status == StudyHallStatus.APPROVED && vendor.isOperational()) {
            status = StudyHallStatus.ACTIVE;
        }
    }

    /** Migrated halls are already live. */
    public void markImported(Long legacyPropertyId, Instant now) {
        this.legacyPropertyId = legacyPropertyId;
        this.status = StudyHallStatus.ACTIVE;
        this.submittedAt = now;
        this.approvedAt = now;
    }

    /** Vendor-controlled visibility: ACTIVE (listed) or INACTIVE (hidden). */
    public void changeVisibility(StudyHallStatus target) {
        if (target == StudyHallStatus.ACTIVE) {
            require(EnumSet.of(StudyHallStatus.APPROVED, StudyHallStatus.INACTIVE), "activate");
            if (!vendor.isOperational()) {
                throw new BusinessRuleException("VENDOR_NOT_APPROVED",
                        "Your vendor account must be approved before a study hall can go live");
            }
        } else if (target == StudyHallStatus.INACTIVE) {
            require(EnumSet.of(StudyHallStatus.APPROVED, StudyHallStatus.ACTIVE), "deactivate");
        } else {
            throw new BusinessRuleException("Vendors can only set a study hall to ACTIVE or INACTIVE");
        }
        status = target;
    }

    /** Admin state machine (see docs/ARCHITECTURE.md). */
    public void apply(StatusAction action, String reason, Instant now) {
        switch (action) {
            case APPROVE -> {
                require(EnumSet.of(StudyHallStatus.PENDING_APPROVAL), "approve");
                approve(now);
            }
            case REJECT -> {
                require(EnumSet.of(StudyHallStatus.PENDING_APPROVAL), "reject");
                requireReason(reason, action);
                status = StudyHallStatus.REJECTED;
            }
            case SUSPEND -> {
                require(EnumSet.of(StudyHallStatus.PENDING_APPROVAL, StudyHallStatus.APPROVED,
                        StudyHallStatus.ACTIVE, StudyHallStatus.INACTIVE), "suspend");
                requireReason(reason, action);
                status = StudyHallStatus.SUSPENDED;
            }
            case ACTIVATE -> {
                require(EnumSet.of(StudyHallStatus.SUSPENDED), "activate");
                status = vendor.isOperational() ? StudyHallStatus.ACTIVE : StudyHallStatus.APPROVED;
                if (approvedAt == null) {
                    approvedAt = now;
                }
            }
        }
        statusReason = reason;
    }

    public boolean isDeletable() {
        return status == StudyHallStatus.DRAFT || status == StudyHallStatus.REJECTED
                || status == StudyHallStatus.PENDING_APPROVAL;
    }

    private void approve(Instant now) {
        status = vendor.isOperational() ? StudyHallStatus.ACTIVE : StudyHallStatus.APPROVED;
        approvedAt = now;
    }

    private void require(EnumSet<StudyHallStatus> allowed, String verb) {
        if (!allowed.contains(status)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot " + verb + " a study hall in status " + status);
        }
    }

    private static void requireReason(String reason, StatusAction action) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("REASON_REQUIRED", "A reason is required to " + action.name().toLowerCase());
        }
    }
}
