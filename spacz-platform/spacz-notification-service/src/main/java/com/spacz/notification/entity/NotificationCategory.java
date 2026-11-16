package com.spacz.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a notification is about. Apps group and filter by it, users can turn push off per category,
 * and Android pushes use the notification channel {@code spacz_<category in lower case>}.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationCategory {
    REGISTRATION("Registration & welcome"),
    ACCOUNT("Account status"),
    /** Results of a review: vendor / study hall approved, rejected, suspended, re-activated. */
    APPROVAL("Approvals"),
    /** Something waiting for the recipient: e.g. a vendor or hall awaiting an admin's approval. */
    REQUEST("Requests"),
    BOOKING("Bookings"),
    ENROLLMENT("Enrollments"),
    UPLOAD("Uploads"),
    OFFER("Offers"),
    ANNOUNCEMENT("Announcements"),
    /** Confirmation of the recipient's own action (profile updated, hall saved ...); in-app only by default. */
    ACTIVITY("Your activity"),
    /** Operational alerts for administrators (new registrations, feedback ...). */
    ADMIN("Admin alerts"),
    SYSTEM("System");

    private final String label;

    public String androidChannelId() {
        return "spacz_" + name().toLowerCase();
    }
}
