package com.spacz.studyhall.payment.dto;

import com.spacz.studyhall.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the payments API. */
public final class PaymentRequests {

    private PaymentRequests() {
    }

    /**
     * @param paymentMethod defaults to ONLINE when omitted. There is deliberately no amount field: the
     *                      amount is always the booking's server-computed total.
     */
    public record CreatePaymentRequest(@NotNull(message = "bookingId is required") Long bookingId,
                                       PaymentMethod paymentMethod) {
    }

    /** Fields returned by the Razorpay Checkout success handler: informational until this backend
     *  verifies gatewaySignature against gatewayOrderId/gatewayPaymentId and asks Razorpay for the status. */
    public record VerifyPaymentRequest(@NotBlank(message = "gatewayOrderId is required") @Size(max = 100) String gatewayOrderId,
                                       @NotBlank(message = "gatewayPaymentId is required") @Size(max = 100) String gatewayPaymentId,
                                       @NotBlank(message = "gatewaySignature is required") @Size(max = 200) String gatewaySignature) {
    }

    /** The QR content after the {@code SPACZ-PAY-HALL:} prefix (the prefix is also accepted and stripped). */
    public record VerifyQrRequest(@NotBlank(message = "qrToken is required") @Size(max = 200) String qrToken) {
    }

    public record VerifyCodeRequest(@NotNull(message = "paymentId is required") Long paymentId,
                                    @NotBlank(message = "code is required")
                                    @Pattern(regexp = "^\\s*\\d{3}\\s?\\d{3}\\s*$", message = "code must be 6 digits")
                                    String code) {
    }

    public record PayAtHallSwitchRequest(@NotNull Boolean enabled) {
    }
}
