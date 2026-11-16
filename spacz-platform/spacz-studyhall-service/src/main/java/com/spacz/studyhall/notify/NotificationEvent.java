package com.spacz.studyhall.notify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A notification for notification-service (same JSON as its {@code POST /internal/notifications}).
 * Personal when {@code recipientUserId} is set (audience = the recipient's role), otherwise a
 * broadcast to every account of the audience (USER, VENDOR, ADMIN or ALL).
 *
 * <p>Categories: REGISTRATION, ACCOUNT, APPROVAL, REQUEST, BOOKING, ENROLLMENT, UPLOAD, OFFER,
 * ANNOUNCEMENT, ACTIVITY, ADMIN, SYSTEM. The {@code dedupKey} makes delivery retries idempotent.
 */
public record NotificationEvent(Long recipientUserId, String audience, String category, String type, String title,
                                String body, String entityType, Long entityId, Map<String, String> data,
                                boolean push, String dedupKey) {

    /** A personal notification with push. */
    public static NotificationEvent toUser(Long userId, String role, String category, String type, String title,
                                           String body) {
        return new NotificationEvent(userId, role, category, type, title, body, null, null, Map.of(), true,
                UUID.randomUUID().toString());
    }

    /** Every account of the audience; for ADMIN this is the admin team's shared inbox. */
    public static NotificationEvent toAudience(String audience, String category, String type, String title, String body) {
        return new NotificationEvent(null, audience, category, type, title, body, null, null, Map.of(), true,
                UUID.randomUUID().toString());
    }

    public static NotificationEvent toAdmins(String category, String type, String title, String body) {
        return toAudience("ADMIN", category, type, title, body);
    }

    public NotificationEvent about(String entityType, Long entityId) {
        return new NotificationEvent(recipientUserId, audience, category, type, title, body, entityType, entityId, data,
                push, dedupKey);
    }

    /** Extra deep-link data for the app (string values). */
    public NotificationEvent with(String key, Object value) {
        if (value == null) {
            return this;
        }
        Map<String, String> copy = new LinkedHashMap<>(data);
        copy.put(key, String.valueOf(value));
        return new NotificationEvent(recipientUserId, audience, category, type, title, body, entityType, entityId, copy,
                push, dedupKey);
    }

    /** Shown in the app's notification list only, no push to the phone. */
    public NotificationEvent inAppOnly() {
        return new NotificationEvent(recipientUserId, audience, category, type, title, body, entityType, entityId, data,
                false, dedupKey);
    }
}
