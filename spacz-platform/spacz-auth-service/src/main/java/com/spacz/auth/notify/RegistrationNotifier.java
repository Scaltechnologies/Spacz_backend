package com.spacz.auth.notify;

import com.spacz.auth.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registration notifications, derived from the registration audit events (email and phone OTP
 * sign-ups alike). They run in the registering transaction and are delivered after it commits.
 */
@Component
@RequiredArgsConstructor
public class RegistrationNotifier {

    private final Notifications notifications;

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        switch (event.action()) {
            case "USER_REGISTERED" -> studentRegistered(event.actorUserId());
            case "VENDOR_REGISTERED" -> vendorRegistered(event.actorUserId(), event.description());
            default -> {
                // other audit events do not notify anyone
            }
        }
    }

    private void studentRegistered(Long userId) {
        // In-app only: the app registers its push device after this first login.
        notifications.send(NotificationEvent.toUser(userId, "USER", "REGISTRATION", "WELCOME",
                        "Welcome to SPACZ!",
                        "Your account is ready. Find a study hall near you and book your seat in a few taps.")
                .about("USER_ACCOUNT", userId).with("screen", "explore").inAppOnly());
        notifications.send(NotificationEvent.toAdmins("ADMIN", "USER_REGISTERED",
                        "New student registered", "Student account #" + userId + " was just created.")
                .about("USER_ACCOUNT", userId).inAppOnly());
    }

    private void vendorRegistered(Long vendorId, String description) {
        notifications.send(NotificationEvent.toUser(vendorId, "VENDOR", "REGISTRATION", "WELCOME_PARTNER",
                        "Welcome to SPACZ Partner!",
                        "Complete your business profile and add your study hall, then submit it for approval.")
                .about("VENDOR", vendorId).with("screen", "vendor-profile").inAppOnly());
        notifications.send(NotificationEvent.toAdmins("ADMIN", "VENDOR_REGISTERED", "New partner registered",
                        (description != null ? description : "Partner #" + vendorId + " registered")
                                + ". They will appear for approval once they submit their profile.")
                .about("VENDOR", vendorId).with("screen", "admin-vendor"));
    }
}
