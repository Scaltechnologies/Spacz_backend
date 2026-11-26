package com.spacz.studyhall.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional body of the admin manual-confirm override; the reason is kept in the booking's status history
 * (440 + the fixed prefix fits the 500-character history column).
 */
public record AdminConfirmBookingRequest(
        @Schema(example = "Paid in cash at the hall; receipt #4411") @Size(max = 440) String reason) {
}
