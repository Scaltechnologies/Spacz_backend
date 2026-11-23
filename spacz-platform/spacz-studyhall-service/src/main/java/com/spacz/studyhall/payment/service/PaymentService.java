package com.spacz.studyhall.payment.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.AuditEventView;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.PaymentView;
import com.spacz.studyhall.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyPaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.dto.VendorPayAtHallResponse;
import com.spacz.studyhall.payment.enums.PaymentMode;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

    PaymentMode currentMode();

    PaymentResponse createPayment(Long userId, CreatePaymentRequest request, String idempotencyKey);

    PaymentResponse getPayment(Long paymentId, Long userId);

    /** Latest payment of the caller's booking (lets the app resume after a restart). */
    PaymentResponse getLatestForBooking(Long bookingId, Long userId);

    PaymentResponse verifyPayment(Long paymentId, Long userId, VerifyPaymentRequest request);

    PaymentResponse cancelPayment(Long paymentId, Long userId);

    PaymentResponse retryPayment(Long paymentId, Long userId);

    /**
     * Before a student cancels a PENDING booking: cancels its open payment, asking the gateway first.
     * Returns the payment's resulting state, or null when the booking had no open payment. If the gateway
     * says the money was captured, the payment ends SUCCESS and the booking CONFIRMED instead.
     */
    PaymentResponse cancelOpenPaymentForBooking(Long bookingId, Long userId);

    void handleWebhook(String rawBody, String signatureHeader);

    /** Ids of ONLINE payments still open past their checkout-session TTL: targets for reconciliation. */
    List<Long> findStalePaymentIds();

    /** Reconciles one stale payment against the gateway's authoritative status. Own transaction per call. */
    void reconcileOnePayment(Long paymentId);

    /** Ids of payments whose money must be returned. */
    List<Long> findRefundPendingIds();

    /** Refunds one REFUND_PENDING payment (idempotent at the gateway). Own transaction per call. */
    void processRefund(Long paymentId);

    // ── Pay at Hall ──────────────────────────────────────────────────────

    /** Vendor scanned a QR: resolves it to the session it belongs to. Does NOT confirm payment. */
    VendorPayAtHallResponse verifyQr(Long vendorId, String qrToken);

    /** Vendor entered the 6-digit code manually. Rate-limited per session (see PayAtHallVerification). */
    VendorPayAtHallResponse verifyCode(Long vendorId, Long paymentId, String code);

    /** The single explicit vendor action that marks the payment SUCCESS and confirms the booking. Idempotent. */
    VendorPayAtHallResponse confirmPayAtHall(Long vendorId, Long paymentId);

    List<VendorPayAtHallResponse> getVendorPending(Long vendorId);

    PageResponse<VendorPayAtHallResponse> getVendorHistory(Long vendorId, Pageable pageable);

    /** Ids of PAY_AT_HALL sessions still open past their TTL. */
    List<Long> findStalePayAtHallIds();

    /** Expires one stale Pay-at-Hall session. Own transaction per call. */
    void expirePayAtHallSession(Long paymentId);

    /** The student's payments of every status (including failed and expired ones), newest first. */
    PageResponse<PaymentResponse> myPayments(Long userId, com.spacz.studyhall.payment.enums.PaymentStatus status,
                                             Pageable pageable);

    // ── Admin (internal) ─────────────────────────────────────────────────

    List<AuditEventView> auditEvents(Long paymentId);

    List<PaymentView> paymentsForBooking(Long bookingId);
}
