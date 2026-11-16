package com.spacz.user.notify;

import com.spacz.user.entity.UserDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Notifications user-service sends. Called inside the business transaction; delivered after commit.
 */
@Component
@RequiredArgsConstructor
public class UserNotifier {

    private final Notifications notifications;

    public void documentUploaded(Long userId, UserDocument document, boolean replaced) {
        String name = switch (document.getDocumentType()) {
            case AADHAAR_FRONT -> "Aadhaar (front)";
            case AADHAAR_BACK -> "Aadhaar (back)";
        };
        notifications.send(NotificationEvent.toUser(userId, "USER", "UPLOAD", "DOCUMENT_UPLOADED",
                        name + (replaced ? " replaced" : " uploaded"),
                        "We received your " + name + ". It will be verified shortly.")
                .about("USER_DOCUMENT", document.getId()).with("screen", "documents").inAppOnly());
        notifications.send(NotificationEvent.toAdmins("ADMIN", "DOCUMENT_UPLOADED", "Student document to verify",
                        "Student #" + userId + " uploaded " + name + ".")
                .about("USER_ACCOUNT", userId).inAppOnly());
    }

    public void feedbackSubmitted(Long userId, Long feedbackId, int rating, String comment) {
        notifications.send(NotificationEvent.toUser(userId, "USER", "ACTIVITY", "FEEDBACK_SUBMITTED",
                        "Thanks for your feedback", "Your " + rating + "-star rating helps us improve SPACZ.")
                .about("FEEDBACK", feedbackId).inAppOnly());
        String excerpt = comment == null ? "" : ": \"" + (comment.length() > 120 ? comment.substring(0, 117) + "..." : comment) + "\"";
        notifications.send(NotificationEvent.toAdmins("ADMIN", "FEEDBACK_RECEIVED", "New app feedback (" + rating + "★)",
                        "Student #" + userId + " rated the app " + rating + "/5" + excerpt)
                .about("FEEDBACK", feedbackId).inAppOnly());
    }

    public void profileUpdated(Long userId, Long profileId) {
        notifications.send(NotificationEvent.toUser(userId, "USER", "ACTIVITY", "PROFILE_UPDATED",
                        "Profile updated", "Your profile changes were saved.")
                .about("USER_PROFILE", profileId).with("screen", "profile").inAppOnly());
    }
}
