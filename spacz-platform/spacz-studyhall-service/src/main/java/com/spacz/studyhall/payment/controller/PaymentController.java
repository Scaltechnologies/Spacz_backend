package com.spacz.studyhall.payment.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.controller.ApiErrorResponses;
import com.spacz.studyhall.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyPaymentRequest;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import com.spacz.studyhall.payment.exception.PaymentException;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The only way a student's booking becomes CONFIRMED: a gateway-verified online payment, or a Pay-at-Hall
 * payment the vendor confirmed. (POST /api/bookings/{id}/confirm is ADMIN-only.)
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pay for a PENDING booking: Razorpay online checkout or Pay at Hall")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class PaymentController {

    private final PaymentService paymentService;

    /** Reads the same cached value payment creation enforces, so the UI and the server always agree. */
    @GetMapping("/mode")
    @Operation(summary = "Which payment method(s) checkout currently offers",
            description = "{\"paymentMode\": \"ONLINE_ONLY\" | \"PAY_AT_HALL_ONLY\" | \"BOTH\"}")
    public Map<String, String> mode() {
        return Map.of("paymentMode", paymentService.currentMode().name());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Start paying for a PENDING booking",
            description = "Send a fresh Idempotency-Key per checkout screen visit. The amount is the booking's "
                    + "server-computed total. ONLINE returns gatewayOrderId + gatewayKeyId for Razorpay Checkout; "
                    + "PAY_AT_HALL returns qrPayload + verificationCode ONCE (store them on the device). An open "
                    + "payment for the booking is returned instead of creating a second one. "
                    + "409 BOOKING_NOT_PAYABLE, 422 BOOKING_HOLD_EXPIRED, 403 ONLINE_PAYMENT_DISABLED / "
                    + "PAY_AT_HALL_DISABLED, 503 PAYMENT_GATEWAY_NOT_CONFIGURED.")
    public PaymentResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  @Valid @RequestBody CreatePaymentRequest request) {
        try {
            return paymentService.createPayment(user.userId(), request, idempotencyKey);
        } catch (DataIntegrityViolationException race) {
            // Lost a race with a concurrent create for the same booking or Idempotency-Key (unique index).
            // Retrying with the same key returns the payment that won.
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_PROCESSING,
                    "A payment for this booking is already being created. Please retry in a moment.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "A payment's current status (poll while it is open)")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return paymentService.getPayment(id, user.userId());
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Latest payment of one of my bookings (resume after an app restart)")
    public PaymentResponse forBooking(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bookingId) {
        return paymentService.getLatestForBooking(bookingId, user.userId());
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Hand over Razorpay Checkout's success result",
            description = "The backend checks the signature AND asks Razorpay for the live status. Returns SUCCESS "
                    + "(booking confirmed), PROCESSING (keep polling) or FAILED. 401 PAYMENT_SIGNATURE_INVALID.")
    public PaymentResponse verify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyPayment(id, user.userId(), request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Checkout dismissed / cancel a Pay-at-Hall order",
            description = "The backend asks the gateway first: a charge that really happened still ends SUCCESS.")
    public PaymentResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return paymentService.cancelPayment(id, user.userId());
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "New gateway attempt after FAILED / CANCELLED / EXPIRED (ONLINE only, seat hold must be live)")
    public PaymentResponse retry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return paymentService.retryPayment(id, user.userId());
    }

    /**
     * Called by Razorpay's servers, not by a logged-in user: permitAll in SecurityConfig and the gateway.
     * Authenticity comes entirely from the HMAC signature over the raw body, verified inside the service.
     * The body is read manually (not bound to a DTO) because the HMAC needs the exact bytes Razorpay signed.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Razorpay webhook (HMAC-authenticated; no bearer token)")
    public ResponseEntity<Void> webhook(HttpServletRequest request,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(readRawBody(request), signature);
        return ResponseEntity.ok().build();
    }

    private static String readRawBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read webhook request body", e);
        }
    }
}
