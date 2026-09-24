package com.spacz.studyhall.dto.booking;

import jakarta.validation.constraints.Size;

public record CancelBookingRequest(@Size(max = 500) String reason) {
}
