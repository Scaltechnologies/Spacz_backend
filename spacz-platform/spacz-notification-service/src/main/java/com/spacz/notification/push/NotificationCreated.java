package com.spacz.notification.push;

/** Published when a notification that asked for a push is saved; handled after commit. */
public record NotificationCreated(Long notificationId) {
}
