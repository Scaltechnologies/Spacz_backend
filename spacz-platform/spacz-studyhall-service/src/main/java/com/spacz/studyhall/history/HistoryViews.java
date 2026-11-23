package com.spacz.studyhall.history;

import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.payment.ledger.PaymentTransaction;
import com.spacz.studyhall.payment.ledger.TransactionStatus;
import com.spacz.studyhall.payment.ledger.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response shapes of the booking-history and transaction-history endpoints. */
public final class HistoryViews {

    private HistoryViews() {
    }

    /** One ledger entry: money received (PAYMENT) or returned (REFUND). */
    public record TransactionView(Long id, String transactionReference, TransactionType type, TransactionStatus status,
                                  Long paymentId, Long bookingId, String bookingReference, Long userId, Long vendorId,
                                  Long studyHallId, PaymentMethod paymentMethod, BigDecimal amount, String currency,
                                  String gatewayProvider, String gatewayOrderId, String gatewayPaymentId,
                                  String gatewayRefundId, Long collectedByVendorId, String description,
                                  Instant occurredAt, Instant completedAt) {

        public static TransactionView of(PaymentTransaction t) {
            return new TransactionView(t.getId(), t.getTransactionReference(), t.getType(), t.getStatus(),
                    t.getPaymentId(), t.getBookingId(), t.getBookingReference(), t.getUserId(), t.getVendorId(),
                    t.getStudyHallId(), t.getPaymentMethod(), t.getAmount(), t.getCurrency(), t.getGatewayProvider(),
                    t.getGatewayOrderId(), t.getGatewayPaymentId(), t.getGatewayRefundId(),
                    t.getCollectedByVendorId(), t.getDescription(), t.getOccurredAt(), t.getCompletedAt());
        }
    }

    /**
     * Totals over the filtered ledger. {@code net = collected - refunded - refundPending}: money that has to
     * go back is not counted as earned.
     *
     * @param collectedByMethod money received, per payment method
     */
    public record TransactionSummary(LocalDate from, LocalDate to, String currency, long paymentCount,
                                     BigDecimal collected, long refundCount, BigDecimal refunded,
                                     BigDecimal refundPending, BigDecimal net,
                                     Map<PaymentMethod, BigDecimal> collectedByMethod) {
    }

    /** One booking status change. {@code fromStatus} is null for the creation row. */
    public record StatusChangeView(BookingStatus fromStatus, BookingStatus toStatus, ActorType actorType,
                                   Long actorId, Long paymentId, String reason, Instant at) {

        public static StatusChangeView of(BookingStatusHistory h) {
            return new StatusChangeView(h.getFromStatus(), h.getToStatus(), h.getActorType(), h.getActorId(),
                    h.getPaymentId(), h.getReason(), h.getCreatedAt());
        }
    }

    /** A payment attempt flow of the booking, whatever its outcome (failed and expired ones included). */
    public record PaymentSummaryView(Long paymentId, PaymentMethod paymentMethod, PaymentStatus status,
                                     BigDecimal amount, String currency, String failureReason, Instant createdAt,
                                     Instant updatedAt) {

        public static PaymentSummaryView of(Payment p) {
            return new PaymentSummaryView(p.getId(), p.getPaymentMethod(), p.getStatus(), p.getFinalAmount(),
                    p.getCurrency(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    /** Everything that happened to one booking, oldest first. */
    public record BookingHistory(Long bookingId, String bookingReference, BookingStatus currentStatus,
                                 BigDecimal totalPrice, List<StatusChangeView> statusHistory,
                                 List<PaymentSummaryView> payments, List<TransactionView> transactions) {
    }
}
