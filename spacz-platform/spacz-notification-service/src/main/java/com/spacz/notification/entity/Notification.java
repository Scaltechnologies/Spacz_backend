package com.spacz.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;

/**
 * One in-app notification: personal ({@code recipientUserId} set) or a broadcast to an
 * {@link Audience}. See V1__initial_schema.sql for how read state is kept.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", updatable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private NotificationCategory category;

    @Column(nullable = false, length = 60, updatable = false)
    private String type;

    @Column(nullable = false, length = 160, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(name = "image_url", length = 500, updatable = false)
    private String imageUrl;

    @Column(name = "entity_type", length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "data_json", columnDefinition = "TEXT", updatable = false)
    private Map<String, String> data;

    @Column(name = "high_priority", nullable = false, updatable = false)
    private boolean highPriority;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "source_service", nullable = false, length = 40, updatable = false)
    private String sourceService;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "dedup_key", length = 120, updatable = false)
    private String dedupKey;

    @Column(name = "push_requested", nullable = false, updatable = false)
    private boolean pushRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 20)
    private PushStatus pushStatus;

    @Column(name = "push_success", nullable = false)
    private int pushSuccess;

    @Column(name = "push_failure", nullable = false)
    private int pushFailure;

    @Column(name = "pushed_at")
    private Instant pushedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Notification(Long recipientUserId, Audience audience, NotificationCategory category, String type,
                         String title, String body, String imageUrl, String entityType, Long entityId,
                         Map<String, String> data, boolean highPriority, String sourceService, Long createdBy,
                         String dedupKey, boolean push) {
        this.recipientUserId = recipientUserId;
        this.audience = audience;
        this.category = category;
        this.type = type;
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.entityType = entityType;
        this.entityId = entityId;
        this.data = data == null ? Map.of() : Map.copyOf(data);
        this.highPriority = highPriority;
        this.sourceService = sourceService;
        this.createdBy = createdBy;
        this.dedupKey = dedupKey;
        this.pushRequested = push;
        this.pushStatus = push ? PushStatus.PENDING : PushStatus.NOT_REQUESTED;
    }

    public boolean isBroadcast() {
        return recipientUserId == null;
    }

    /** Personal notifications only; broadcast read state is a {@link NotificationReceipt}. */
    public void markRead(Instant now) {
        if (isBroadcast()) {
            throw new IllegalStateException("Broadcast read state is kept per account in notification_receipts");
        }
        if (readAt == null) {
            readAt = now;
        }
    }

    public void recordPush(PushStatus status, int success, int failure, Instant now) {
        this.pushStatus = status;
        this.pushSuccess = success;
        this.pushFailure = failure;
        this.pushedAt = now;
    }
}
