package com.spacz.studyhall.payment.ledger;

import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.entity.PaymentAttempt;
import com.spacz.studyhall.wallet.service.WalletLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * Writes the money ledger. Called only by PaymentServiceImpl, inside its transaction and under the payment
 * row lock, so a ledger row commits together with the payment status change it records. Every method is
 * idempotent: (payment_id, type) is unique and an existing row is returned rather than duplicated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionLedger {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentTransactionRepository repository;
    private final WalletLedger walletLedger;
    private final Clock clock;

    /**
     * Money was received for this payment.
     *
     * @param capturedAttempt     the Razorpay attempt that captured it (null for Pay at Hall)
     * @param collectedByVendorId Pay at Hall: the vendor who confirmed the collection
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentTransaction recordPaymentReceived(Payment payment, PaymentAttempt capturedAttempt,
                                                    Long collectedByVendorId) {
        return repository.findByPaymentIdAndType(payment.getId(), TransactionType.PAYMENT).orElseGet(() -> {
            Instant now = clock.instant();
            PaymentTransaction txn = repository.save(
                    withGateway(base(payment, TransactionType.PAYMENT, TransactionStatus.SUCCESS, now), capturedAttempt)
                            .collectedByVendorId(collectedByVendorId)
                            .description(payment.getDescription())
                            .completedAt(now)
                            .build());
            log.info("Ledger {}: PAYMENT {} {} for payment {} (booking {})", txn.getTransactionReference(),
                    txn.getAmount(), txn.getPaymentMethod(), payment.getId(), payment.getBookingId());
            walletLedger.onPaymentReceived(payment);
            return txn;
        });
    }

    /** A full refund of this payment was initiated (the refund job completes it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentTransaction recordRefundInitiated(Payment payment, PaymentAttempt capturedAttempt, String reason) {
        return repository.findByPaymentIdAndType(payment.getId(), TransactionType.REFUND).orElseGet(() -> {
            PaymentTransaction txn = repository.save(
                    withGateway(base(payment, TransactionType.REFUND, TransactionStatus.PENDING, clock.instant()),
                            capturedAttempt)
                            .description(truncate(reason))
                            .build());
            log.info("Ledger {}: REFUND {} initiated for payment {}", txn.getTransactionReference(), txn.getAmount(),
                    payment.getId());
            walletLedger.onRefundInitiated(payment);
            return txn;
        });
    }

    /** The gateway accepted the refund. Creates the REFUND row first if it is missing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentTransaction recordRefundCompleted(Payment payment, PaymentAttempt capturedAttempt,
                                                    String gatewayRefundId) {
        PaymentTransaction txn = recordRefundInitiated(payment, capturedAttempt, payment.getFailureReason());
        if (txn.getStatus() != TransactionStatus.SUCCESS) {
            txn.setStatus(TransactionStatus.SUCCESS);
            txn.setGatewayRefundId(gatewayRefundId);
            txn.setCompletedAt(payment.getRefundedAt() != null ? payment.getRefundedAt() : clock.instant());
            repository.save(txn);
        }
        return txn;
    }

    private static PaymentTransaction.PaymentTransactionBuilder base(Payment payment, TransactionType type,
                                                                     TransactionStatus status, Instant occurredAt) {
        return PaymentTransaction.builder()
                .transactionReference(newReference())
                .type(type)
                .status(status)
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .bookingReference(payment.getBookingReference())
                .userId(payment.getUserId())
                .vendorId(payment.getVendorId())
                .studyHallId(payment.getStudyHallId())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getFinalAmount())
                .currency(payment.getCurrency())
                .occurredAt(occurredAt);
    }

    private static PaymentTransaction.PaymentTransactionBuilder withGateway(
            PaymentTransaction.PaymentTransactionBuilder builder, PaymentAttempt attempt) {
        if (attempt == null) {
            return builder;
        }
        return builder.gatewayProvider(attempt.getGatewayProvider())
                .gatewayOrderId(attempt.getGatewayOrderId())
                .gatewayPaymentId(attempt.getGatewayPaymentId());
    }

    static String newReference() {
        StringBuilder reference = new StringBuilder("TXN-");
        for (int i = 0; i < 12; i++) {
            reference.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
