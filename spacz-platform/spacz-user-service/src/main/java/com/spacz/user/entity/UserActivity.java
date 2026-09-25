package com.spacz.user.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Append-only history entry. Booking events are reported by studyhall-service.
 */
@Entity
@Table(name = "user_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40, updatable = false)
    private ActivityType type;

    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "reference_type", length = 40, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserActivity of(Long userId, ActivityType type, String description, String referenceType,
                                  Long referenceId) {
        UserActivity activity = new UserActivity();
        activity.userId = userId;
        activity.type = type;
        activity.description = description;
        activity.referenceType = referenceType;
        activity.referenceId = referenceId;
        return activity;
    }
}
