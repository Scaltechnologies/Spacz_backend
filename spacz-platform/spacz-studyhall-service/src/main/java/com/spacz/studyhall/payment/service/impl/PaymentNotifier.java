package com.spacz.studyhall.payment.service.impl;

import com.spacz.studyhall.notify.NotificationEvent;
import com.spacz.studyhall.notify.Notifications;
import com.spacz.studyhall.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Payment notifications (BOOKING category). Best-effort by construction: Notifications only publishes an
 * event that is delivered after commit, and every call here swallows its own errors, so a notification
 * can never block or roll back payment logic. (The booking-confirmed notifications themselves come from
 * the BOOKING_CONFIRMED booking activity, as for any confirmation.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotifier {

    private final Notifications notifications;

    public void payAtHallCreated(Payment payment) {
        send(NotificationEvent.toUser(payment.getVendorId(), "VENDOR", "BOOKING", "PAY_AT_HALL_CREATED",
                        "Pay at Hall order", "A student will pay Rs." + payment.getFinalAmount() + " at your hall for "
                                + payment.getDescription() + ". Scan their QR or enter their code, then confirm payment.")
                .about("PAYMENT", payment.getId()).with("screen", "vendor-pay-at-hall")
                .with("bookingId", payment.getBookingId()));
        send(NotificationEvent.toUser(payment.getUserId(), "USER", "BOOKING", "PAY_AT_HALL_CREATED",
                        "Pay at the hall to confirm", "Show your QR or code at the counter and pay Rs."
                                + payment.getFinalAmount() + " to confirm booking " + payment.getBookingReference() + ".")
                .about("PAYMENT", payment.getId()).with("screen", "pay-at-hall")
                .with("bookingId", payment.getBookingId()).inAppOnly());
    }

    public void payAtHallExpired(Payment payment) {
        send(NotificationEvent.toUser(payment.getUserId(), "USER", "BOOKING", "PAY_AT_HALL_EXPIRED",
                        "Pay at Hall order expired", "Your Pay at Hall order for booking " + payment.getBookingReference()
                                + " expired without payment. Book again to get a seat.")
                .about("PAYMENT", payment.getId()).with("bookingId", payment.getBookingId()));
    }

    public void paymentFailed(Payment payment) {
        send(NotificationEvent.toUser(payment.getUserId(), "USER", "BOOKING", "PAYMENT_FAILED", "Payment failed",
                        "Your payment of Rs." + payment.getFinalAmount() + " for booking " + payment.getBookingReference()
                                + " could not be completed. You can retry while your seat is held.")
                .about("PAYMENT", payment.getId()).with("screen", "booking").with("bookingId", payment.getBookingId())
                .inAppOnly());
    }

    public void refundQueued(Payment payment) {
        send(NotificationEvent.toUser(payment.getUserId(), "USER", "BOOKING", "PAYMENT_REFUND_INITIATED",
                        "Refund on its way", "We received Rs." + payment.getFinalAmount() + " for booking "
                                + payment.getBookingReference() + " but the seat was no longer available. "
                                + "The full amount is being refunded to your original payment method.")
                .about("PAYMENT", payment.getId()).with("bookingId", payment.getBookingId()));
    }

    private void send(NotificationEvent event) {
        try {
            notifications.send(event);
        } catch (RuntimeException ex) {
            log.warn("Could not queue {} notification: {}", event.type(), ex.getMessage());
        }
    }
}
