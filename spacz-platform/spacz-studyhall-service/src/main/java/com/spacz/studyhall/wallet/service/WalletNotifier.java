package com.spacz.studyhall.wallet.service;

import com.spacz.studyhall.notify.NotificationEvent;
import com.spacz.studyhall.notify.Notifications;
import com.spacz.studyhall.wallet.entity.PayoutMethod;
import com.spacz.studyhall.wallet.entity.Withdrawal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Withdrawal notifications (vendor: PAYOUT category; admins: REQUEST). Delivered after commit; a failure to
 * queue one never affects the withdrawal itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletNotifier {

    private final Notifications notifications;

    public void requested(Withdrawal w, String vendorName) {
        send(NotificationEvent.toAdmins("REQUEST", "WITHDRAWAL_REQUESTED", "Withdrawal request",
                        vendorName + " requested Rs." + w.getAmount() + " to " + destination(w) + ". Ref " + w.getReference() + ".")
                .about("WITHDRAWAL", w.getId()).with("screen", "admin-withdrawal").with("withdrawalId", w.getId()));
        send(vendor(w, "WITHDRAWAL_REQUESTED", "Withdrawal requested",
                "We received your request to withdraw Rs." + w.getAmount() + " to " + destination(w)
                        + ". Ref " + w.getReference() + ".").inAppOnly());
    }

    public void processing(Withdrawal w) {
        send(vendor(w, "WITHDRAWAL_PROCESSING", "Withdrawal being processed",
                "Your withdrawal of Rs." + w.getAmount() + " (" + w.getReference() + ") is being sent to "
                        + destination(w) + "."));
    }

    public void paid(Withdrawal w) {
        send(vendor(w, "WITHDRAWAL_PAID", "Withdrawal paid",
                "Rs." + w.getAmount() + " was sent to " + destination(w) + ". Transfer reference "
                        + w.getPayoutReference() + "."));
    }

    public void rejected(Withdrawal w) {
        send(vendor(w, "WITHDRAWAL_REJECTED", "Withdrawal not processed",
                "Your withdrawal of Rs." + w.getAmount() + " (" + w.getReference() + ") was declined and the amount "
                        + "is back in your wallet. Reason: " + w.getRejectionReason()));
    }

    public void cancelled(Withdrawal w) {
        send(vendor(w, "WITHDRAWAL_CANCELLED", "Withdrawal cancelled",
                "You cancelled withdrawal " + w.getReference() + ". Rs." + w.getAmount() + " is back in your wallet.")
                .inAppOnly());
    }

    private static NotificationEvent vendor(Withdrawal w, String type, String title, String body) {
        return NotificationEvent.toUser(w.getVendorId(), "VENDOR", "PAYOUT", type, title, body)
                .about("WITHDRAWAL", w.getId()).with("screen", "vendor-withdrawal").with("withdrawalId", w.getId());
    }

    private static String destination(Withdrawal w) {
        if (w.getPayoutMethod() == PayoutMethod.UPI) {
            return "UPI " + w.getUpiId();
        }
        String n = w.getAccountNumber();
        return "bank account ••" + (n == null ? "" : n.substring(Math.max(0, n.length() - 4)));
    }

    private void send(NotificationEvent event) {
        try {
            notifications.send(event);
        } catch (RuntimeException ex) {
            log.warn("Could not queue {} notification: {}", event.type(), ex.getMessage());
        }
    }
}
