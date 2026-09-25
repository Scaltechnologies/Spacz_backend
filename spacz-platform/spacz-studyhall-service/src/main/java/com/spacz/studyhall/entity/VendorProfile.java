package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A vendor's (institute's) business profile and approval status. {@code vendorId} is the
 * auth-service account ID. Legacy: the {@code owner} table of the Partner service.
 */
@Entity
@Table(name = "vendor_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VendorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "vendor_id", nullable = false, unique = true, updatable = false)
    private Long vendorId;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(length = 2000)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorStatus status;

    @Setter(AccessLevel.NONE)
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Setter(AccessLevel.NONE)
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Legacy {@code owner.owner_id}, set by the MySQL migration (idempotency and traceability). */
    @Column(name = "legacy_owner_id", unique = true)
    private Long legacyOwnerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static VendorProfile draft(Long vendorId) {
        VendorProfile vendor = new VendorProfile();
        vendor.vendorId = vendorId;
        vendor.status = VendorStatus.DRAFT;
        return vendor;
    }

    public boolean isOperational() {
        return status.isOperational();
    }

    /** Missing fields that block submission; empty when the profile is complete. */
    public List<String> missingForSubmission() {
        List<String> missing = new ArrayList<>();
        if (isBlank(businessName)) {
            missing.add("businessName");
        }
        if (isBlank(contactName)) {
            missing.add("contactName");
        }
        if (isBlank(phone)) {
            missing.add("phone");
        }
        if (isBlank(addressLine)) {
            missing.add("addressLine");
        }
        if (isBlank(city)) {
            missing.add("city");
        }
        return missing;
    }

    /** DRAFT / REJECTED → PENDING. */
    public void submit(Instant now) {
        if (status != VendorStatus.DRAFT && status != VendorStatus.REJECTED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION", "Cannot submit a vendor in status " + status);
        }
        List<String> missing = missingForSubmission();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("PROFILE_INCOMPLETE", "Complete these fields before submitting: " + missing);
        }
        status = VendorStatus.PENDING;
        submittedAt = now;
        statusReason = null;
    }

    /** Used for vendors created through the legacy Partner API, which has no explicit submit step. */
    public void submitWithoutCompletenessCheck(Instant now) {
        if (status == VendorStatus.DRAFT) {
            status = VendorStatus.PENDING;
            submittedAt = now;
        }
    }

    /** Vendors migrated from the legacy system are already live. */
    public void markImported(Long legacyOwnerId, Instant now) {
        this.legacyOwnerId = legacyOwnerId;
        this.status = VendorStatus.APPROVED;
        this.submittedAt = now;
        this.approvedAt = now;
        this.statusReason = "Migrated from the legacy SPACZ Partner system";
    }

    /** Admin state machine (see docs/ARCHITECTURE.md). */
    public void apply(StatusAction action, String reason, Instant now) {
        switch (action) {
            case APPROVE -> {
                require(EnumSet.of(VendorStatus.PENDING, VendorStatus.REJECTED), action);
                status = VendorStatus.APPROVED;
                approvedAt = now;
            }
            case REJECT -> {
                require(EnumSet.of(VendorStatus.PENDING), action);
                requireReason(reason, action);
                status = VendorStatus.REJECTED;
            }
            case SUSPEND -> {
                require(EnumSet.of(VendorStatus.APPROVED, VendorStatus.ACTIVE, VendorStatus.INACTIVE), action);
                requireReason(reason, action);
                status = VendorStatus.SUSPENDED;
            }
            case ACTIVATE -> {
                require(EnumSet.of(VendorStatus.SUSPENDED, VendorStatus.INACTIVE), action);
                status = VendorStatus.ACTIVE;
                if (approvedAt == null) {
                    approvedAt = now;
                }
            }
        }
        statusReason = reason;
    }

    /** Name shown to students: business name, falling back to the contact name. */
    public String displayName() {
        return !isBlank(businessName) ? businessName : contactName;
    }

    private void require(EnumSet<VendorStatus> allowed, StatusAction action) {
        if (!allowed.contains(status)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot " + action.name().toLowerCase() + " a vendor in status " + status);
        }
    }

    private static void requireReason(String reason, StatusAction action) {
        if (isBlank(reason)) {
            throw new BusinessRuleException("REASON_REQUIRED", "A reason is required to " + action.name().toLowerCase());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
