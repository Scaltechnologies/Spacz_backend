package com.spacz.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One account's state for one broadcast: it exists once the account has read the broadcast, and
 * {@code dismissedAt} is set when the account removed it from its inbox.
 */
@Entity
@Table(name = "notification_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    public static NotificationReceipt read(Long notificationId, Long userId, Instant now) {
        NotificationReceipt receipt = new NotificationReceipt();
        receipt.notificationId = notificationId;
        receipt.userId = userId;
        receipt.readAt = now;
        return receipt;
    }

    public void dismiss(Instant now) {
        if (dismissedAt == null) {
            dismissedAt = now;
        }
    }
}
