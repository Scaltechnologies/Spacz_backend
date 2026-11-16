package com.spacz.admin.notify;

import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.entity.Offer;
import com.spacz.admin.entity.PlatformUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Notifications caused by administrators. Vendor and study-hall approvals are notified by
 * studyhall-service (which applies them), not here.
 */
@Component
@RequiredArgsConstructor
public class AdminNotifier {

    private final Notifications notifications;

    public void accountSuspended(AccountDto account, String reason) {
        String body = "Your SPACZ account has been suspended"
                + (reason == null || reason.isBlank() ? "." : ". Reason: " + reason.trim())
                + " Contact SPACZ support if you think this is a mistake.";
        notifications.send(NotificationEvent.toUser(account.id(), account.role(), "ACCOUNT", "ACCOUNT_SUSPENDED",
                "Account suspended", body).about("USER_ACCOUNT", account.id()));
    }

    public void accountActivated(AccountDto account) {
        notifications.send(NotificationEvent.toUser(account.id(), account.role(), "ACCOUNT", "ACCOUNT_ACTIVATED",
                        "Account active again", "Your SPACZ account is active again. Welcome back!")
                .about("USER_ACCOUNT", account.id()));
    }

    /** An offer students can use right now (offers that start later are not announced). */
    public void offerLive(Offer offer) {
        String body = offer.getDescription() != null && !offer.getDescription().isBlank()
                ? offer.getDescription() : "Save on your next study hall booking.";
        notifications.send(NotificationEvent.toAudience("USER", "OFFER", "OFFER_LIVE", "New offer: " + offer.getTitle(), body)
                .about("OFFER", offer.getId()).with("screen", "offers").with("offerId", offer.getId()));
    }

    public void platformUpdatePublished(PlatformUpdate update) {
        notifications.send(NotificationEvent.toAudience("USER", "ANNOUNCEMENT", "PLATFORM_UPDATE", update.getTitle(),
                        update.getDescription())
                .about("PLATFORM_UPDATE", update.getId()).with("screen", "updates").with("updateId", update.getId()));
    }
}
