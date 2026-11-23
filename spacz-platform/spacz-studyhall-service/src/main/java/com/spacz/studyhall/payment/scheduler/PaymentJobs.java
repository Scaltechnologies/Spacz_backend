package com.spacz.studyhall.payment.scheduler;

import com.spacz.studyhall.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Payment safety nets. Each id is processed through the {@code paymentService} Spring proxy (never a
 * self-call), one independent transaction per id: a failure on one payment must not roll back or block
 * the others in the same sweep. Safe on several instances: every step re-checks the status under the
 * payment row lock.
 *
 * <ul>
 *   <li>Reconciliation: a payment can go quiet because the app was killed, the network dropped, or a
 *       webhook never arrived. Asks the gateway directly about ONLINE payments still open past their TTL.</li>
 *   <li>Pay-at-Hall expiry: sessions not vendor-confirmed in time become EXPIRED (no gateway involved).</li>
 *   <li>Refunds: captured payments whose booking could not be confirmed (REFUND_PENDING) are refunded.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentJobs {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${spacz.payment.reconcile.interval-ms:300000}", initialDelayString = "${spacz.payment.jobs.initial-delay-ms:45000}")
    public void reconcileStalePayments() {
        sweep("Payment reconciliation", paymentService.findStalePaymentIds(), paymentService::reconcileOnePayment);
    }

    @Scheduled(fixedDelayString = "${spacz.payment.pay-at-hall.expiry-sweep-interval-ms:300000}", initialDelayString = "${spacz.payment.jobs.initial-delay-ms:50000}")
    public void expirePayAtHallSessions() {
        sweep("Pay at Hall expiry", paymentService.findStalePayAtHallIds(), paymentService::expirePayAtHallSession);
    }

    @Scheduled(fixedDelayString = "${spacz.payment.refund.interval-ms:120000}", initialDelayString = "${spacz.payment.jobs.initial-delay-ms:55000}")
    public void processRefunds() {
        sweep("Payment refund", paymentService.findRefundPendingIds(), paymentService::processRefund);
    }

    private static void sweep(String name, List<Long> ids, Consumer<Long> action) {
        if (ids.isEmpty()) return;
        log.info("{} sweep: {} payment(s)", name, ids.size());
        for (Long paymentId : ids) {
            try {
                action.accept(paymentId);
            } catch (Exception e) {
                log.error("{} failed for paymentId={}: {}", name, paymentId, e.getMessage(), e);
            }
        }
    }
}
