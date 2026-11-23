package com.spacz.studyhall.payment.service.impl;

import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.payment.config.PaymentProperties;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.AttemptView;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.AuditEventView;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.PaymentView;
import com.spacz.studyhall.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyPaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.dto.VendorPayAtHallResponse;
import com.spacz.studyhall.payment.entity.PayAtHallVerification;
import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.entity.PaymentAttempt;
import com.spacz.studyhall.payment.entity.PaymentAuditEvent;
import com.spacz.studyhall.payment.entity.PaymentWebhookEvent;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentMode;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.payment.enums.VerificationMethod;
import com.spacz.studyhall.payment.exception.PaymentException;
import com.spacz.studyhall.payment.gateway.CreateOrderCommand;
import com.spacz.studyhall.payment.gateway.GatewayOrder;
import com.spacz.studyhall.payment.gateway.GatewayPaymentState;
import com.spacz.studyhall.payment.gateway.GatewayRefund;
import com.spacz.studyhall.payment.gateway.GatewayStatusResult;
import com.spacz.studyhall.payment.gateway.PaymentGateway;
import com.spacz.studyhall.payment.gateway.PaymentModeConfigClient;
import com.spacz.studyhall.payment.gateway.WebhookVerificationResult;
import com.spacz.studyhall.payment.ledger.TransactionLedger;
import com.spacz.studyhall.history.ActorType;
import com.spacz.studyhall.payment.repository.PayAtHallVerificationRepository;
import com.spacz.studyhall.payment.repository.PaymentAttemptRepository;
import com.spacz.studyhall.payment.repository.PaymentAuditEventRepository;
import com.spacz.studyhall.payment.repository.PaymentRepository;
import com.spacz.studyhall.payment.repository.PaymentWebhookEventRepository;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.BookingService.PaidConfirmation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the payment state machine end to end: creation, gateway order, client-side verification,
 * webhook processing, retry, user cancellation, stale-session reconciliation, refunds and Pay at Hall.
 *
 * <p>The single choke point for confirming a paid booking is {@link #finalizeSuccessCore}, reached
 * from the online path only through {@link #resolveAttemptOutcome}, which always runs under a
 * pessimistic row lock on the parent {@link Payment}. Every entry path (verify, webhook, cancel,
 * retry, reconciliation sweep, vendor confirm) funnels through it, which is what makes a duplicate
 * webhook, a verify/webhook race, or a retried reconciliation incapable of confirming twice.
 *
 * <p>Lock order everywhere: payment row, then Pay-at-Hall verification row, then (inside
 * BookingService#confirmAfterPayment) the seat row. Booking creation takes only the seat lock.
 *
 * <p>Methods that persist a security-relevant write and then throw (wrong code, lockout, expiry,
 * invalid signature, gateway failure) use {@code noRollbackFor = PaymentException.class}, so the write
 * survives the error response. Anything that must be atomic with a successful confirmation throws
 * only SpaczException (which still rolls back) or throws before writing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    static final String QR_PREFIX = "SPACZ-PAY-HALL:";
    private static final List<PaymentStatus> PAY_AT_HALL_OPEN =
            List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_VENDOR_CONFIRMATION);
    private static final List<PaymentStatus> ONLINE_STALE = List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final WebhookEventStore webhookEventStore;
    private final PaymentAuditEventRepository paymentAuditEventRepository;
    private final PayAtHallVerificationRepository payAtHallVerificationRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final PaymentModeConfigClient paymentModeConfigClient;
    private final PaymentProperties properties;
    private final BookingProperties bookingProperties;
    private final PaymentNotifier notifier;
    private final TransactionLedger ledger;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PaymentMode currentMode() {
        return paymentModeConfigClient.getCurrentMode();
    }

    // ── Creation ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentResponse createPayment(Long userId, CreatePaymentRequest request, String idempotencyKey) {
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null;
        if (key != null) {
            var existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                Payment p = existing.get();
                if (!p.getUserId().equals(userId)) {
                    throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment not found");
                }
                return toResponse(p, latestAttemptOrNull(p.getId()));
            }
        }

        Booking booking = bookingRepository.findDetailed(request.bookingId())
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.bookingId()));
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new PaymentException(PaymentErrorCode.BOOKING_NOT_PAYABLE,
                    "This booking is " + booking.getStatus() + " and cannot be paid for");
        }
        if (booking.isHoldExpired(now)) {
            throw new PaymentException(PaymentErrorCode.BOOKING_HOLD_EXPIRED,
                    "The seat hold has expired. Please book again.");
        }

        // One open payment per booking: return it instead of opening a second session. The partial
        // unique index uq_payments_one_open_per_booking is the backstop for concurrent requests (the
        // controller turns that violation into 409 PAYMENT_ALREADY_PROCESSING).
        var open = paymentRepository.findFirstByBookingIdAndStatusIn(booking.getId(), PaymentStatus.OPEN);
        if (open.isPresent()) {
            return toResponse(open.get(), latestAttemptOrNull(open.get().getId()));
        }

        PaymentMethod method = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.ONLINE;
        requireMethodAllowed(method);

        BigDecimal amount = booking.getTotalPrice(); // server-priced at booking creation; never from the client
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentException(PaymentErrorCode.BOOKING_NOT_PAYABLE, "This booking has no payable amount");
        }

        if (method == PaymentMethod.PAY_AT_HALL) {
            return createPayAtHallPayment(userId, booking, amount, key, now);
        }

        paymentGateway.getPublicKeyId(); // fail fast (503) if the gateway isn't configured

        Instant expiresAt = now.plus(properties.sessionTtl());
        Payment payment = paymentRepository.saveAndFlush(newPayment(userId, booking, amount, PaymentMethod.ONLINE,
                PaymentStatus.CREATED, expiresAt, key));
        bookingService.extendHoldForPayment(booking.getId(), expiresAt.plus(properties.holdGrace()));

        PaymentAttempt attempt = openAttempt(payment, 1);
        payment.transitionTo(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        recordAudit(payment.getId(), "USER", userId, "PAYMENT_CREATED", null, payment.getStatus().name(), "ONLINE");
        return toResponse(payment, attempt);
    }

    private void requireMethodAllowed(PaymentMethod method) {
        PaymentMode mode = paymentModeConfigClient.getCurrentMode();
        if (method == PaymentMethod.PAY_AT_HALL && mode == PaymentMode.ONLINE_ONLY) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_DISABLED,
                    "Pay at Hall isn't available right now. Please pay online.");
        }
        if (method == PaymentMethod.ONLINE && mode == PaymentMode.PAY_AT_HALL_ONLY) {
            throw new PaymentException(PaymentErrorCode.ONLINE_PAYMENT_DISABLED,
                    "Online payment isn't available right now. Please pay at the hall.");
        }
    }

    private Payment newPayment(Long userId, Booking booking, BigDecimal amount, PaymentMethod method,
                               PaymentStatus status, Instant expiresAt, String idempotencyKey) {
        StudyHall hall = booking.getStudyHall();
        return Payment.builder()
                .userId(userId)
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .studyHallId(hall.getId())
                .seatId(booking.getSeat().getId())
                .vendorId(hall.getVendor().getVendorId())
                .description(truncate(hall.getName() + " · Seat " + booking.getSeat().getSeatNumber()
                        + " · " + booking.getStartDate() + "→" + booking.getEndDate(), 255))
                .finalAmount(amount)
                .currency("INR")
                .status(status)
                .paymentMethod(method)
                .expiresAt(expiresAt)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    // ── Pay at Hall: creation ──────────────────────────────────────────────────

    private PaymentResponse createPayAtHallPayment(Long userId, Booking booking, BigDecimal amount,
                                                   String idempotencyKey, Instant now) {
        VendorProfile vendor = booking.getStudyHall().getVendor();
        if (!vendor.isPayAtHallEnabled()) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_DISABLED,
                    "This study hall does not currently accept Pay at Hall. Please pay online.");
        }

        // A held seat blocks other students, so the window is short (default 4h) and never runs past
        // the end of the booking's first day in the business time zone.
        Instant ttlEnd = now.plus(properties.payAtHall().sessionTtl());
        Instant startDayEnd = booking.getStartDate().plusDays(1).atStartOfDay(bookingProperties.zone()).toInstant();
        Instant expiresAt = ttlEnd.isBefore(startDayEnd) ? ttlEnd : startDayEnd;
        if (!expiresAt.isAfter(now)) {
            throw new PaymentException(PaymentErrorCode.BOOKING_NOT_PAYABLE,
                    "This booking starts too soon to pay at the hall. Please pay online.");
        }

        Payment payment = paymentRepository.saveAndFlush(newPayment(userId, booking, amount, PaymentMethod.PAY_AT_HALL,
                PaymentStatus.PENDING, expiresAt, idempotencyKey));
        bookingService.extendHoldForPayment(booking.getId(), expiresAt);

        String rawQrToken = randomToken();
        String rawCode = randomSixDigitCode();
        payAtHallVerificationRepository.save(PayAtHallVerification.builder()
                .paymentId(payment.getId())
                .qrTokenHash(sha256(rawQrToken))
                .verificationCodeHash(sha256(rawCode))
                .attemptCount(0)
                .build());

        recordAudit(payment.getId(), "USER", userId, "PAYMENT_CREATED", null, payment.getStatus().name(), "PAY_AT_HALL");
        notifier.payAtHallCreated(payment);

        // The only time the raw token/code ever leave this service.
        return toResponse(payment, null).toBuilder()
                .qrPayload(QR_PREFIX + rawQrToken)
                .verificationCode(rawCode)
                .build();
    }

    private PaymentAttempt openAttempt(Payment payment, int attemptNumber) {
        GatewayOrder order;
        try {
            order = paymentGateway.createOrder(CreateOrderCommand.builder()
                    .amount(payment.getFinalAmount())
                    .currency(payment.getCurrency())
                    .receipt("payment-" + payment.getId() + "-attempt-" + attemptNumber) // Razorpay max 40 chars
                    .notes(Map.of(
                            "paymentId", String.valueOf(payment.getId()),
                            "userId", String.valueOf(payment.getUserId()),
                            "bookingId", String.valueOf(payment.getBookingId()),
                            "bookingReference", payment.getBookingReference()))
                    .build());
        } catch (PaymentException e) {
            // Nothing was ever charged: safe to fail the payment outright rather than leave it open.
            // (Persisted despite the exception: noRollbackFor on the callers.)
            payment.transitionTo(PaymentStatus.FAILED);
            payment.setFailureReason("Could not initiate payment with the gateway: " + e.getMessage());
            paymentRepository.save(payment);
            recordAudit(payment.getId(), "SYSTEM", null, "GATEWAY_ORDER_FAILED", null, "FAILED", e.getCode().name());
            throw e;
        }

        return paymentAttemptRepository.save(PaymentAttempt.builder()
                .paymentId(payment.getId())
                .attemptNumber(attemptNumber)
                .gatewayProvider(paymentGateway.getProviderName())
                .gatewayOrderId(order.getGatewayOrderId())
                .status(PaymentStatus.PENDING)
                .build());
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long userId) {
        Payment payment = findOwned(paymentId, userId);
        return toResponse(payment, latestAttemptOrNull(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getLatestForBooking(Long bookingId, Long userId) {
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDescIdDesc(bookingId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found for booking " + bookingId));
        return toResponse(payment, latestAttemptOrNull(payment.getId()));
    }

    // ── Client-side verification ────────────────────────────────────────────

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentResponse verifyPayment(Long paymentId, Long userId, VerifyPaymentRequest request) {
        Payment payment = findOwned(paymentId, userId);
        PaymentAttempt attempt = paymentAttemptRepository.findByGatewayOrderId(request.gatewayOrderId())
                .filter(a -> a.getPaymentId().equals(paymentId))
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_REFERENCE_MISMATCH,
                        "This payment reference does not match any attempt on this payment"));

        if (payment.getStatus().isMoneySettled()) {
            return toResponse(payment, attempt); // already finalized: idempotent
        }

        boolean signatureOk = paymentGateway.verifyCheckoutSignature(
                request.gatewayOrderId(), request.gatewayPaymentId(), request.gatewaySignature());
        if (!signatureOk) {
            attempt.setSignatureVerified(false); // persisted: noRollbackFor
            paymentAttemptRepository.save(attempt);
            log.warn("Invalid checkout signature for payment {} attempt {}", paymentId, attempt.getId());
            throw new PaymentException(PaymentErrorCode.PAYMENT_SIGNATURE_INVALID,
                    "Payment signature could not be verified");
        }

        // The signature proves the (orderId, paymentId) pair came from the gateway, but not that the
        // payment has actually been captured: cross-check the live status too.
        GatewayStatusResult status = paymentGateway.fetchOrderStatus(request.gatewayOrderId());
        // Lock order payment -> attempt, as in every other path (the webhook locks the payment, then
        // updates the attempt). Writing the attempt first would let verify and webhook deadlock.
        lockPayment(paymentId);
        attempt.setSignatureVerified(true);
        paymentAttemptRepository.save(attempt);
        return resolveAttemptOutcome(paymentId, attempt.getId(), status);
    }

    // ── Webhook ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        WebhookVerificationResult result = paymentGateway.verifyAndParseWebhook(rawBody, signatureHeader);
        if (!result.isSignatureValid()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_SIGNATURE_INVALID, "Webhook signature verification failed");
        }

        GatewayStatusResult status = result.getStatus();
        String dedupeKey = truncate(result.getGatewayEventId() != null
                ? result.getGatewayEventId()
                : (status.getGatewayOrderId() + ":" + status.getGatewayPaymentId() + ":" + result.getEventType()), 200);

        Long eventId = claimWebhookEvent(dedupeKey, truncate(result.getEventType(), 100), rawBody);
        if (eventId == null) {
            log.info("Duplicate/replayed webhook delivery ignored: dedupeKey={}", dedupeKey);
            return; // already processed once: a duplicate webhook has zero additional effect
        }

        try {
            String outcome;
            Long attemptId = null;
            if (status.getGatewayOrderId() == null) {
                outcome = "IGNORED_NO_ORDER_REFERENCE";
            } else {
                PaymentAttempt attempt = paymentAttemptRepository.findByGatewayOrderId(status.getGatewayOrderId()).orElse(null);
                if (attempt == null) {
                    outcome = "IGNORED_UNKNOWN_PAYMENT";
                    log.warn("Webhook for unrecognized gateway order id: {}", status.getGatewayOrderId());
                } else {
                    attemptId = attempt.getId();
                    resolveAttemptOutcome(attempt.getPaymentId(), attempt.getId(), status);
                    outcome = "PROCESSED";
                }
            }
            // Written in THIS transaction: "PROCESSED" commits atomically with the effect. If the commit
            // fails, the row stays NULL and the next delivery reprocesses it.
            PaymentWebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
            event.setProcessingResult(outcome);
            event.setPaymentAttemptId(attemptId);
            event.setProcessedAt(clock.instant());
        } catch (RuntimeException e) {
            webhookEventStore.markError(eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * Returns the id of the dedupe row to process, or null when this event was already processed. The
     * insert runs in its own transaction (see WebhookEventStore); rows whose result is NULL (a previous
     * attempt crashed or rolled back) or ERROR are reprocessed so Razorpay's retries are never blocked.
     */
    private Long claimWebhookEvent(String dedupeKey, String eventType, String rawBody) {
        PaymentWebhookEvent existing = webhookEventStore.find(dedupeKey);
        if (existing == null) {
            try {
                return webhookEventStore.insert(dedupeKey, eventType, rawBody);
            } catch (DataIntegrityViolationException race) {
                existing = webhookEventStore.find(dedupeKey);
                if (existing == null) {
                    throw race;
                }
            }
        }
        return existing.isFinal() ? null : existing.getId();
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId, Long userId) {
        Payment payment = findOwned(paymentId, userId);
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_SUCCESSFUL,
                    "This payment has already succeeded and cannot be cancelled");
        }
        if (!payment.getStatus().isOpen()) {
            return toResponse(payment, latestAttemptOrNull(paymentId));
        }

        PaymentAttempt attempt = latestAttemptOrNull(paymentId);
        if (attempt == null) {
            // Pay at Hall, or the gateway order was never created: no money could have moved.
            return finalizeCancelDirect(paymentId, null, "USER", userId);
        }

        // A charge attempt exists: never take the user's word for cancellation over the gateway's.
        GatewayStatusResult status = fetchGatewayStatusSafely(attempt.getGatewayOrderId());
        boolean noPaymentAtGateway = status.getGatewayPaymentId() == null && attempt.getGatewayPaymentId() == null;
        if (status.getState() == GatewayPaymentState.SUCCESS
                || (status.getState() != GatewayPaymentState.FAILED && !noPaymentAtGateway)) {
            // Captured, or a payment is in flight (authorized / status unknown): let the gateway decide.
            return resolveAttemptOutcome(paymentId, attempt.getId(), status);
        }
        // Nothing reached the gateway's payment stage (or it failed): safe to cancel. A charge that still
        // lands later is reconciled forward by the webhook (CANCELLED -> SUCCESS / REFUND_PENDING).
        return finalizeCancelDirect(paymentId, attempt.getId(), "USER", userId);
    }

    @Override
    @Transactional
    public PaymentResponse cancelOpenPaymentForBooking(Long bookingId, Long userId) {
        Payment open = paymentRepository.findFirstByBookingIdAndStatusIn(bookingId, PaymentStatus.OPEN)
                .filter(p -> p.getUserId().equals(userId))
                .orElse(null);
        return open == null ? null : cancelPayment(open.getId(), userId);
    }

    private PaymentResponse finalizeCancelDirect(Long paymentId, Long attemptId, String actorType, Long actorId) {
        Payment payment = lockPayment(paymentId);
        PaymentAttempt attempt = attemptId != null ? paymentAttemptRepository.findById(attemptId).orElse(null) : null;
        if (!payment.getStatus().isOpen()) {
            return toResponse(payment, attempt);
        }
        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.CANCELLED);
        payment.setFailureReason("Cancelled by user");
        paymentRepository.save(payment);
        if (attempt != null && attempt.getStatus() != PaymentStatus.SUCCESS) {
            attempt.setStatus(PaymentStatus.CANCELLED);
            paymentAttemptRepository.save(attempt);
        }
        recordAudit(paymentId, actorType, actorId, "PAYMENT_CANCELLED", prev, "CANCELLED", null);
        return toResponse(payment, attempt);
    }

    // ── Retry ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentResponse retryPayment(Long paymentId, Long userId) {
        Payment payment = findOwned(paymentId, userId);
        if (payment.getPaymentMethod() == PaymentMethod.PAY_AT_HALL) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_NOT_SUPPORTED,
                    "Pay at Hall payments cannot be retried. Create a new payment for the booking instead.");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_SUCCESSFUL,
                    "This payment has already succeeded; no retry needed");
        }
        if (payment.getStatus().isMoneySettled()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_RETRY_NOT_ALLOWED,
                    "This payment was captured and is being refunded; it cannot be retried");
        }

        if (payment.getStatus().isOpen()) {
            PaymentAttempt latest = latestAttemptOrNull(paymentId);
            if (latest == null) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSING,
                        "This payment is still being initiated. Please wait before retrying.");
            }
            // Reconcile the existing attempt first: never open a second charge while the first might still land.
            GatewayStatusResult status = fetchGatewayStatusSafely(latest.getGatewayOrderId());
            PaymentResponse reconciled = resolveAttemptOutcome(paymentId, latest.getId(), status);
            if (reconciled.status().isMoneySettled()) {
                return reconciled; // it had actually succeeded: nothing to retry
            }
            if (!reconciled.status().isTerminal()) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSING,
                        "This payment is still being processed by the gateway. Please wait before retrying.");
            }
        }

        payment = lockPayment(paymentId);
        if (!payment.getStatus().isTerminal() || payment.getStatus().isMoneySettled()) {
            return toResponse(payment, latestAttemptOrNull(paymentId)); // changed concurrently
        }

        Instant now = clock.instant();
        Long bookingId = payment.getBookingId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new PaymentException(PaymentErrorCode.BOOKING_NOT_PAYABLE,
                    "This booking is " + booking.getStatus() + " and cannot be paid for");
        }
        if (booking.isHoldExpired(now)) {
            throw new PaymentException(PaymentErrorCode.BOOKING_HOLD_EXPIRED,
                    "The seat hold has expired. Please book again.");
        }
        Long otherOpen = paymentRepository.findFirstByBookingIdAndStatusIn(booking.getId(), PaymentStatus.OPEN)
                .map(Payment::getId).orElse(null);
        if (otherOpen != null && !otherOpen.equals(paymentId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSING,
                    "Another payment (" + otherOpen + ") is already open for this booking");
        }
        requireMethodAllowed(PaymentMethod.ONLINE);
        paymentGateway.getPublicKeyId();

        int nextAttemptNumber = paymentAttemptRepository.countByPaymentId(paymentId) + 1;
        Instant expiresAt = now.plus(properties.sessionTtl());
        payment.setFailureReason(null);
        payment.setExpiresAt(expiresAt);
        bookingService.extendHoldForPayment(booking.getId(), expiresAt.plus(properties.holdGrace()));
        String prev = payment.getStatus().name();
        PaymentAttempt attempt = openAttempt(payment, nextAttemptNumber);
        payment.transitionTo(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        recordAudit(paymentId, "USER", userId, "PAYMENT_RETRIED", prev, "PENDING", "attempt " + nextAttemptNumber);
        return toResponse(payment, attempt);
    }

    // ── Reconciliation sweep ─────────────────────────────────────────────────

    /**
     * Resolves ONLINE sessions still PENDING/PROCESSING after their checkout TTL. A payment whose
     * gateway status is genuinely still uncertain becomes EXPIRED (never FAILED: the money might yet
     * land, in which case the webhook reconciles it forward), so the user can safely retry.
     */
    @Override
    @Transactional
    public void reconcileOnePayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.getStatus().isOpen() || payment.getPaymentMethod() != PaymentMethod.ONLINE) return;

        PaymentAttempt latest = latestAttemptOrNull(paymentId);
        if (latest == null) {
            expireSession(paymentId);
            return;
        }

        GatewayStatusResult status = fetchGatewayStatusSafely(latest.getGatewayOrderId());
        if (status.getState() == GatewayPaymentState.SUCCESS || status.getState() == GatewayPaymentState.FAILED) {
            resolveAttemptOutcome(paymentId, latest.getId(), status);
        } else {
            expireSession(paymentId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findStalePaymentIds() {
        return paymentRepository.findIdsByMethodAndStatusInAndExpiresAtBefore(PaymentMethod.ONLINE, ONLINE_STALE,
                clock.instant());
    }

    private void expireSession(Long paymentId) {
        Payment payment = lockPayment(paymentId);
        if (!payment.getStatus().isOpen()) return;
        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.EXPIRED);
        payment.setFailureReason("Payment session expired without gateway confirmation");
        paymentRepository.save(payment);
        recordAudit(paymentId, "SYSTEM", null, "PAYMENT_EXPIRED", prev, "EXPIRED", null);
    }

    // ── The single atomic, idempotent finalize choke point (ONLINE) ─────────────

    /**
     * Locks the parent Payment, then resolves the given attempt's gateway-reported outcome against it.
     * The first check ("money already settled? return as-is") is what makes a duplicate success
     * delivery, however it arrives, a guaranteed no-op rather than a second confirmation.
     */
    private PaymentResponse resolveAttemptOutcome(Long paymentId, Long attemptId, GatewayStatusResult gatewayStatus) {
        Payment payment = lockPayment(paymentId);
        PaymentAttempt attempt = paymentAttemptRepository.findById(attemptId).orElseThrow(
                () -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment attempt not found"));
        entityManager.refresh(attempt); // attempts only change under the payment lock: read the latest

        if (payment.getStatus().isMoneySettled()) {
            return toResponse(payment, attempt); // duplicate SUCCESS -> zero additional effect
        }
        if (payment.getStatus().isTerminal() && gatewayStatus.getState() != GatewayPaymentState.SUCCESS) {
            // Already FAILED/CANCELLED/EXPIRED and the gateway doesn't contradict that: nothing to do.
            return toResponse(payment, attempt);
        }
        if (payment.getStatus().isTerminal()) {
            log.warn("Payment {} was locally {} but the gateway now reports SUCCESS for attempt {}: reconciling "
                    + "forward rather than losing a real charge", payment.getId(), payment.getStatus(), attempt.getId());
        }

        return switch (gatewayStatus.getState()) {
            case SUCCESS -> {
                if (gatewayStatus.getAmount() != null
                        && gatewayStatus.getAmount().compareTo(payment.getFinalAmount()) != 0) {
                    // Money WAS captured, just not the expected amount: return it rather than keep it.
                    yield refundRequired(payment, attempt, gatewayStatus, "AMOUNT_MISMATCH",
                            "Gateway-confirmed amount (" + gatewayStatus.getAmount()
                                    + ") does not match the expected charge (" + payment.getFinalAmount() + ")");
                }
                yield doFinalizeSuccess(payment, attempt, gatewayStatus);
            }
            case FAILED -> doFinalizeFailure(payment, attempt, gatewayStatus);
            case PENDING, UNKNOWN -> {
                attempt.setStatus(PaymentStatus.PROCESSING);
                if (attempt.getGatewayPaymentId() == null && gatewayStatus.getGatewayPaymentId() != null) {
                    attempt.setGatewayPaymentId(gatewayStatus.getGatewayPaymentId());
                }
                paymentAttemptRepository.save(attempt);
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.transitionTo(PaymentStatus.PROCESSING);
                    paymentRepository.save(payment);
                }
                yield toResponse(payment, attempt);
            }
        };
    }

    private PaymentResponse doFinalizeSuccess(Payment payment, PaymentAttempt attempt, GatewayStatusResult gatewayStatus) {
        markAttemptCaptured(attempt, gatewayStatus);
        finalizeSuccessCore(payment, null, null);
        log.info("Payment {}: paymentId={} bookingId={} userId={} amount={} gatewayPaymentId={}",
                payment.getStatus(), payment.getId(), payment.getBookingId(), payment.getUserId(),
                payment.getFinalAmount(), attempt.getGatewayPaymentId());
        return toResponse(payment, attempt);
    }

    /** The money was captured on this attempt, whatever happens to the payment afterwards. */
    private void markAttemptCaptured(PaymentAttempt attempt, GatewayStatusResult gatewayStatus) {
        if (gatewayStatus.getGatewayPaymentId() != null) {
            attempt.setGatewayPaymentId(gatewayStatus.getGatewayPaymentId());
        }
        attempt.setStatus(PaymentStatus.SUCCESS);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        paymentAttemptRepository.save(attempt);
    }

    private PaymentResponse refundRequired(Payment payment, PaymentAttempt attempt, GatewayStatusResult gatewayStatus,
                                           String code, String reason) {
        markAttemptCaptured(attempt, gatewayStatus);
        queueRefund(payment, code, reason);
        return toResponse(payment, attempt);
    }

    private void queueRefund(Payment payment, String code, String reason) {
        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.REFUND_PENDING);
        payment.setFailureReason(truncate(reason + "; a full refund has been initiated", 500));
        paymentRepository.saveAndFlush(payment);
        recordAudit(payment.getId(), "SYSTEM", null, "REFUND_REQUIRED", prev, "REFUND_PENDING", code);
        // The money was captured (and is now owed back): both movements go into the ledger.
        PaymentAttempt captured = capturedAttemptOrNull(payment.getId());
        ledger.recordPaymentReceived(payment, captured, null);
        ledger.recordRefundInitiated(payment, captured, payment.getFailureReason());
        notifier.refundQueued(payment);
        log.warn("Payment {} captured but booking {} not confirmed ({}): refund queued",
                payment.getId(), payment.getBookingId(), code);
    }

    /**
     * The single place that confirms a paid booking, shared by the ONLINE gateway-verified path
     * ({@link #doFinalizeSuccess}) and the Pay-at-Hall vendor confirmation ({@link #confirmPayAtHall}).
     * Caller holds the Payment row lock. Booking confirmation and Payment SUCCESS commit together.
     */
    private void finalizeSuccessCore(Payment payment, Long confirmedByVendorId, VerificationMethod method) {
        PaidConfirmation result = bookingService.confirmAfterPayment(payment.getBookingId(), payment.getId(),
                confirmedByVendorId != null ? ActorType.VENDOR : ActorType.SYSTEM, confirmedByVendorId);

        String problem = null;
        if (result == PaidConfirmation.SEAT_LOST) {
            problem = "SEAT_LOST";
        } else if (result == PaidConfirmation.ALREADY_CONFIRMED && paymentRepository.existsByBookingIdAndStatusAndIdNot(
                payment.getBookingId(), PaymentStatus.SUCCESS, payment.getId())) {
            problem = "DUPLICATE_PAYMENT"; // the booking was already paid by another payment
        }

        if (problem != null) {
            if (payment.getPaymentMethod() == PaymentMethod.PAY_AT_HALL) {
                // No money moved through us: close the session and tell the vendor not to collect.
                // (Persisted although we throw: confirmPayAtHall has noRollbackFor = PaymentException.)
                String prev = payment.getStatus().name();
                payment.transitionTo(PaymentStatus.CANCELLED);
                payment.setFailureReason(problem.equals("SEAT_LOST")
                        ? "The booking is no longer valid (seat released)" : "The booking was already paid");
                paymentRepository.save(payment);
                recordAudit(payment.getId(), "SYSTEM", null, "PAYMENT_NOT_CONFIRMABLE", prev, "CANCELLED", problem);
                throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CONFIRMABLE, problem.equals("SEAT_LOST")
                        ? "This booking is no longer valid (seat released). Do not collect payment."
                        : "This booking has already been paid. Do not collect payment.");
            }
            queueRefund(payment, problem, problem.equals("SEAT_LOST")
                    ? "Seat was no longer available when the payment was confirmed"
                    : "The booking had already been paid by another payment");
            return;
        }

        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        paymentRepository.saveAndFlush(payment);
        ledger.recordPaymentReceived(payment,
                payment.getPaymentMethod() == PaymentMethod.ONLINE ? capturedAttemptOrNull(payment.getId()) : null,
                confirmedByVendorId);
        recordAudit(payment.getId(), confirmedByVendorId != null ? "VENDOR" : "SYSTEM", confirmedByVendorId,
                "BOOKING_CONFIRMED", prev, "SUCCESS",
                (method != null ? "method=" + method + " " : "") + "result=" + result);
    }

    private PaymentResponse doFinalizeFailure(Payment payment, PaymentAttempt attempt, GatewayStatusResult gatewayStatus) {
        String reason = gatewayStatus.getErrorDescription();
        attempt.setStatus(PaymentStatus.FAILED);
        attempt.setFailureCode(truncate(gatewayStatus.getErrorCode(), 100));
        attempt.setFailureReason(truncate(reason, 500));
        if (attempt.getGatewayPaymentId() == null && gatewayStatus.getGatewayPaymentId() != null) {
            attempt.setGatewayPaymentId(gatewayStatus.getGatewayPaymentId());
        }
        paymentAttemptRepository.save(attempt);

        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.FAILED);
        payment.setFailureReason(truncate(reason != null ? reason : "Payment could not be completed", 500));
        paymentRepository.save(payment);
        recordAudit(payment.getId(), "SYSTEM", null, "PAYMENT_FAILED", prev, "FAILED",
                truncate(gatewayStatus.getErrorCode(), 100));
        notifier.paymentFailed(payment);

        log.info("Payment FAILED: paymentId={} userId={} code={} reason={}",
                payment.getId(), payment.getUserId(), gatewayStatus.getErrorCode(), reason);
        return toResponse(payment, attempt);
    }

    // ── Refunds ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Long> findRefundPendingIds() {
        return paymentRepository.findIdsByStatus(PaymentStatus.REFUND_PENDING);
    }

    @Override
    @Transactional
    public void processRefund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return;
        payment = lockPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) return;
        PaymentAttempt captured = capturedAttemptOrNull(paymentId);
        if (captured == null) {
            log.error("Payment {} is REFUND_PENDING but has no captured attempt; needs manual review", paymentId);
            return;
        }
        // Idempotent at the gateway: a refund created by an earlier run (whose commit failed) is found, not repeated.
        GatewayRefund refund = paymentGateway.ensureFullRefund(captured.getGatewayPaymentId(),
                payment.getFinalAmount(), "refund-" + paymentId);
        payment.setGatewayRefundId(refund.getGatewayRefundId());
        payment.setRefundedAt(clock.instant());
        payment.transitionTo(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        ledger.recordRefundCompleted(payment, captured, refund.getGatewayRefundId());
        recordAudit(paymentId, "SYSTEM", null, "REFUNDED", "REFUND_PENDING", "REFUNDED",
                "refundId=" + refund.getGatewayRefundId() + " status=" + refund.getStatus());
        log.info("Payment {} refunded: refundId={} status={}", paymentId, refund.getGatewayRefundId(), refund.getStatus());
    }

    // ── Pay at Hall: vendor verification & confirmation ────────────────────────

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public VendorPayAtHallResponse verifyQr(Long vendorId, String qrToken) {
        String token = qrToken.trim();
        if (token.startsWith(QR_PREFIX)) {
            token = token.substring(QR_PREFIX.length());
        }
        PayAtHallVerification found = payAtHallVerificationRepository.findByQrTokenHash(sha256(token))
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.INVALID_QR, "QR code not recognized"));
        Payment payment = lockPayment(found.getPaymentId());
        PayAtHallVerification verification = lockVerification(payment.getId());
        return resolveVerification(vendorId, payment, verification, VerificationMethod.QR);
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public VendorPayAtHallResponse verifyCode(Long vendorId, Long paymentId, String code) {
        Payment payment = lockPayment(paymentId);
        if (payment.getPaymentMethod() != PaymentMethod.PAY_AT_HALL) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_NOT_SUPPORTED, "Not a Pay at Hall payment");
        }
        PayAtHallVerification verification = lockVerification(paymentId);

        if (!payment.getVendorId().equals(vendorId)) {
            recordAudit(paymentId, "VENDOR", vendorId, "CODE_VERIFICATION_FAILED", payment.getStatus().name(),
                    payment.getStatus().name(), "wrong vendor");
            throw new PaymentException(PaymentErrorCode.WRONG_VENDOR, "This order does not belong to your study hall");
        }
        Instant now = clock.instant();
        if (verification.getLockedUntil() != null && verification.getLockedUntil().isAfter(now)) {
            throw new PaymentException(PaymentErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many incorrect attempts. Try again after " + verification.getLockedUntil());
        }

        String normalized = code == null ? "" : code.replaceAll("\\s", "");
        if (!MessageDigest.isEqual(sha256(normalized).getBytes(StandardCharsets.UTF_8),
                verification.getVerificationCodeHash().getBytes(StandardCharsets.UTF_8))) {
            int max = properties.payAtHall().maxCodeAttempts();
            int attempts = verification.getAttemptCount() + 1;
            boolean lockedNow = attempts >= max;
            if (lockedNow) {
                verification.setLockedUntil(now.plus(properties.payAtHall().lockout()));
                verification.setAttemptCount(0); // a fresh set of attempts once the lockout has passed
            } else {
                verification.setAttemptCount(attempts);
            }
            payAtHallVerificationRepository.save(verification); // persisted although we throw: noRollbackFor
            recordAudit(paymentId, "VENDOR", vendorId, "CODE_VERIFICATION_FAILED", payment.getStatus().name(),
                    payment.getStatus().name(), "attempt " + attempts + (lockedNow ? ", locked" : ""));
            if (lockedNow) {
                throw new PaymentException(PaymentErrorCode.TOO_MANY_ATTEMPTS, "Too many incorrect attempts. Locked for "
                        + properties.payAtHall().lockout().toMinutes() + " minutes.");
            }
            int left = max - attempts;
            throw new PaymentException(PaymentErrorCode.INVALID_PAYMENT_CODE,
                    "Incorrect payment code. " + left + " attempt" + (left == 1 ? "" : "s") + " left before a lock.");
        }

        verification.setAttemptCount(0);
        verification.setLockedUntil(null);
        return resolveVerification(vendorId, payment, verification, VerificationMethod.CODE);
    }

    /**
     * Shared post-verification checks/transition for both QR and code entry. Verifying does NOT
     * confirm payment: it only proves the vendor is looking at the right session. The vendor must then
     * call confirmPayAtHall explicitly.
     */
    private VendorPayAtHallResponse resolveVerification(Long vendorId, Payment payment,
                                                        PayAtHallVerification verification, VerificationMethod method) {
        String action = method == VerificationMethod.QR ? "QR_SCANNED" : "CODE_VERIFIED";
        if (payment.getPaymentMethod() != PaymentMethod.PAY_AT_HALL) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_NOT_SUPPORTED, "Not a Pay at Hall payment");
        }
        if (!payment.getVendorId().equals(vendorId)) {
            recordAudit(payment.getId(), "VENDOR", vendorId, action, payment.getStatus().name(),
                    payment.getStatus().name(), "wrong vendor");
            throw new PaymentException(PaymentErrorCode.WRONG_VENDOR, "This order does not belong to your study hall");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_SUCCESSFUL, "This payment has already been confirmed");
        }
        if (!payment.getStatus().isOpen()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CONFIRMABLE,
                    "This payment is " + payment.getStatus() + " and cannot be verified");
        }
        expireIfPastTtl(payment);

        String prevStatus = payment.getStatus().name();
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.transitionTo(PaymentStatus.AWAITING_VENDOR_CONFIRMATION);
            paymentRepository.save(payment);
        }
        verification.setConfirmationMethod(method);
        payAtHallVerificationRepository.save(verification);
        recordAudit(payment.getId(), "VENDOR", vendorId, action, prevStatus, payment.getStatus().name(), null);
        return toVendorResponse(payment, verification, bookingsById(List.of(payment)).get(payment.getBookingId()));
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public VendorPayAtHallResponse confirmPayAtHall(Long vendorId, Long paymentId) {
        Payment payment = lockPayment(paymentId);
        if (payment.getPaymentMethod() != PaymentMethod.PAY_AT_HALL) {
            throw new PaymentException(PaymentErrorCode.PAY_AT_HALL_NOT_SUPPORTED, "Not a Pay at Hall payment");
        }
        PayAtHallVerification verification = lockVerification(paymentId);
        if (!payment.getVendorId().equals(vendorId)) {
            throw new PaymentException(PaymentErrorCode.WRONG_VENDOR, "This order does not belong to your study hall");
        }
        // Idempotent: two taps, a network retry, or two vendor devices racing on the same confirmation
        // all resolve to this same already-SUCCESS row, with zero additional effect.
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return toVendorResponse(payment, verification, bookingsById(List.of(payment)).get(payment.getBookingId()));
        }
        if (!payment.getStatus().isOpen()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CONFIRMABLE,
                    "This payment is " + payment.getStatus() + " and cannot be confirmed");
        }
        expireIfPastTtl(payment);
        if (payment.getStatus() != PaymentStatus.AWAITING_VENDOR_CONFIRMATION) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_CONFIRMABLE,
                    "Scan the student's QR code or enter their 6-digit code before confirming payment");
        }

        String prevStatus = payment.getStatus().name();
        recordAudit(paymentId, "VENDOR", vendorId, "PAYMENT_CONFIRMATION_STARTED", prevStatus, prevStatus, null);

        VerificationMethod method = verification.getConfirmationMethod() != null
                ? verification.getConfirmationMethod() : VerificationMethod.CODE;
        finalizeSuccessCore(payment, vendorId, method);

        verification.setConfirmedByVendorId(vendorId);
        verification.setConfirmedAt(clock.instant());
        verification.setConfirmationMethod(method);
        payAtHallVerificationRepository.save(verification);
        recordAudit(paymentId, "VENDOR", vendorId, "PAYMENT_CONFIRMED", prevStatus, "SUCCESS", null);

        log.info("Pay at Hall payment CONFIRMED: paymentId={} vendorId={} bookingId={} amount={}",
                paymentId, vendorId, payment.getBookingId(), payment.getFinalAmount());
        return toVendorResponse(payment, verification, bookingsById(List.of(payment)).get(payment.getBookingId()));
    }

    /** Persists EXPIRED (noRollbackFor) and throws when the session's TTL has passed. */
    private void expireIfPastTtl(Payment payment) {
        if (!payment.getExpiresAt().isAfter(clock.instant())) {
            String prev = payment.getStatus().name();
            payment.transitionTo(PaymentStatus.EXPIRED);
            payment.setFailureReason("Pay at Hall session expired");
            paymentRepository.save(payment);
            recordAudit(payment.getId(), "SYSTEM", null, "PAYMENT_EXPIRED", prev, "EXPIRED", null);
            throw new PaymentException(PaymentErrorCode.PAYMENT_EXPIRED, "This Pay at Hall session has expired");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorPayAtHallResponse> getVendorPending(Long vendorId) {
        List<Payment> payments = paymentRepository.findByVendorIdAndPaymentMethodAndStatusInOrderByCreatedAtAsc(
                vendorId, PaymentMethod.PAY_AT_HALL, PAY_AT_HALL_OPEN);
        Map<Long, Booking> bookings = bookingsById(payments);
        return payments.stream()
                .map(p -> toVendorResponse(p, payAtHallVerificationRepository.findByPaymentId(p.getId()).orElse(null),
                        bookings.get(p.getBookingId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VendorPayAtHallResponse> getVendorHistory(Long vendorId, Pageable pageable) {
        var page = paymentRepository.findByVendorIdAndPaymentMethodOrderByCreatedAtDesc(vendorId,
                PaymentMethod.PAY_AT_HALL, pageable);
        Map<Long, Booking> bookings = bookingsById(page.getContent());
        return PageResponse.from(page, p -> toVendorResponse(p,
                payAtHallVerificationRepository.findByPaymentId(p.getId()).orElse(null), bookings.get(p.getBookingId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findStalePayAtHallIds() {
        return paymentRepository.findIdsByMethodAndStatusInAndExpiresAtBefore(PaymentMethod.PAY_AT_HALL,
                PAY_AT_HALL_OPEN, clock.instant());
    }

    @Override
    @Transactional
    public void expirePayAtHallSession(Long paymentId) {
        if (paymentRepository.findById(paymentId).isEmpty()) return;
        Payment payment = lockPayment(paymentId);
        if (!payment.getStatus().isOpen() || payment.getExpiresAt().isAfter(clock.instant())) return;
        String prev = payment.getStatus().name();
        payment.transitionTo(PaymentStatus.EXPIRED);
        payment.setFailureReason("Pay at Hall session expired without vendor confirmation");
        paymentRepository.save(payment);
        recordAudit(paymentId, "SYSTEM", null, "PAYMENT_EXPIRED", prev, "EXPIRED", null);
        notifier.payAtHallExpired(payment);
        // The booking hold was set to the same instant; BookingLifecycleJob releases the seat.
    }

    private VendorPayAtHallResponse toVendorResponse(Payment payment, PayAtHallVerification verification, Booking booking) {
        return VendorPayAtHallResponse.builder()
                .paymentId(payment.getId())
                .userId(payment.getUserId())
                .bookingId(payment.getBookingId())
                .bookingReference(payment.getBookingReference())
                .studyHallId(payment.getStudyHallId())
                .seatId(payment.getSeatId())
                .seatNumber(booking != null ? booking.getSeat().getSeatNumber() : null)
                .startDate(booking != null ? booking.getStartDate() : null)
                .endDate(booking != null ? booking.getEndDate() : null)
                .description(payment.getDescription())
                .amount(payment.getFinalAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .expiresAt(payment.getExpiresAt())
                .confirmedAt(verification != null ? verification.getConfirmedAt() : null)
                .confirmedByVendorId(verification != null ? verification.getConfirmedByVendorId() : null)
                .confirmationMethod(verification != null ? verification.getConfirmationMethod() : null)
                .build();
    }

    private Map<Long, Booking> bookingsById(List<Payment> payments) {
        if (payments.isEmpty()) return Map.of();
        return bookingRepository.findAllById(payments.stream().map(Payment::getBookingId).distinct().toList())
                .stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
    }

    // ── Student payment history ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> myPayments(Long userId, PaymentStatus status, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Payment> page = status == null
                ? paymentRepository.findByUserId(userId, newestFirst)
                : paymentRepository.findByUserIdAndStatus(userId, status, newestFirst);
        // Closed payments need no attempt (no checkout to reopen); open ones get theirs for resuming.
        return PageResponse.from(page, p -> toResponse(p, p.getStatus().isOpen() ? latestAttemptOrNull(p.getId()) : null));
    }

    // ── Admin (internal) ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventView> auditEvents(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment not found: " + paymentId);
        }
        return paymentAuditEventRepository.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId).stream()
                .map(e -> new AuditEventView(e.getId(), e.getPaymentId(), e.getActorType(), e.getActorId(),
                        e.getAction(), e.getPreviousStatus(), e.getNewStatus(), e.getMetadata(), e.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentView> paymentsForBooking(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId).stream()
                .map(p -> new PaymentView(p.getId(), p.getBookingId(), p.getBookingReference(), p.getUserId(),
                        p.getVendorId(), p.getPaymentMethod(), p.getStatus(), p.getFinalAmount(), p.getCurrency(),
                        p.getFailureReason(), p.getExpiresAt(), p.getGatewayRefundId(), p.getRefundedAt(),
                        p.getCreatedAt(), p.getUpdatedAt(),
                        paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberDesc(p.getId()).stream()
                                .map(a -> new AttemptView(a.getAttemptNumber(), a.getGatewayProvider(),
                                        a.getGatewayOrderId(), a.getGatewayPaymentId(), a.getStatus(),
                                        a.getFailureCode(), a.getFailureReason(), a.getSignatureVerified(),
                                        a.getCreatedAt()))
                                .toList()))
                .toList();
    }

    // ── Security primitives ─────────────────────────────────────────────────

    /** 32 random bytes, URL-safe base64, no padding: unguessable, never derived from an id or a timestamp.
     *  Only its SHA-256 hash is ever persisted. */
    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Uniform over 000000-999999 (leading zeros allowed). */
    private static String randomSixDigitCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void recordAudit(Long paymentId, String actorType, Long actorId, String action,
                             String previousStatus, String newStatus, String metadata) {
        paymentAuditEventRepository.save(PaymentAuditEvent.builder()
                .paymentId(paymentId)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .metadata(truncate(metadata, 500))
                .build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * SELECT ... FOR UPDATE on the payment row AND a reload of its state. (A plain locking query would
     * return an instance already loaded earlier in this transaction with its possibly stale state, so
     * the "already SUCCESS?" check could miss a concurrent finalize.)
     */
    private Payment lockPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(
                () -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        entityManager.flush(); // refresh discards unflushed changes: write this transaction's changes first
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        return payment;
    }

    private PayAtHallVerification lockVerification(Long paymentId) {
        PayAtHallVerification verification = payAtHallVerificationRepository.findByPaymentId(paymentId).orElseThrow(
                () -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        entityManager.flush();
        entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
        return verification;
    }

    private Payment findOwned(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND,
                        "Payment not found with id: " + paymentId));
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Payment not found with id: " + paymentId);
        }
        return payment;
    }

    /** The attempt whose money was captured, if any. */
    private PaymentAttempt capturedAttemptOrNull(Long paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberDesc(paymentId).stream()
                .filter(a -> a.getStatus() == PaymentStatus.SUCCESS && a.getGatewayPaymentId() != null)
                .findFirst().orElse(null);
    }

    private PaymentAttempt latestAttemptOrNull(Long paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberDesc(paymentId)
                .stream().findFirst().orElse(null);
    }

    private GatewayStatusResult fetchGatewayStatusSafely(String gatewayOrderId) {
        try {
            return paymentGateway.fetchOrderStatus(gatewayOrderId);
        } catch (Exception e) {
            log.warn("Gateway status fetch failed for order={}: {}", gatewayOrderId, e.getMessage());
            return GatewayStatusResult.unknown(gatewayOrderId);
        }
    }

    private static String truncate(String message, int max) {
        if (message == null) return null;
        return message.length() > max ? message.substring(0, max) : message;
    }

    private PaymentResponse toResponse(Payment payment, PaymentAttempt attempt) {
        boolean checkoutOpen = attempt != null && payment.getStatus().isOpen()
                && payment.getPaymentMethod() == PaymentMethod.ONLINE;
        String keyId = null;
        if (checkoutOpen) {
            try {
                keyId = paymentGateway.getPublicKeyId();
            } catch (PaymentException ignored) {
                // Gateway config missing at read time: omit the key rather than fail a status read.
            }
        }
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .bookingId(payment.getBookingId())
                .bookingReference(payment.getBookingReference())
                .studyHallId(payment.getStudyHallId())
                .seatId(payment.getSeatId())
                .vendorId(payment.getVendorId())
                .description(payment.getDescription())
                .amount(payment.getFinalAmount())
                .currency(payment.getCurrency())
                .failureReason(payment.getFailureReason())
                .expiresAt(payment.getExpiresAt())
                .confirmedAt(payment.getStatus() == PaymentStatus.SUCCESS ? payment.getUpdatedAt() : null)
                .gatewayProvider(checkoutOpen ? attempt.getGatewayProvider() : null)
                .gatewayOrderId(checkoutOpen ? attempt.getGatewayOrderId() : null)
                .gatewayKeyId(keyId)
                .attemptNumber(attempt != null ? attempt.getAttemptNumber() : 0)
                .build();
    }
}
