package com.spacz.notification.entity;

public enum PushStatus {
    /** In-app only; no push was requested. */
    NOT_REQUESTED,
    /** Waiting for the push to be sent (happens right after the notification is saved). */
    PENDING,
    /** Delivered to FCM for at least one device. */
    SENT,
    /** FCM rejected every device. */
    FAILED,
    /** The recipient(s) have no registered device. */
    NO_DEVICES,
    /** The recipient turned push off for this category. */
    MUTED,
    /** Push is not configured on the server (no Firebase credentials). */
    DISABLED
}
