package com.spacz.admin.dto;

import jakarta.validation.constraints.Size;

public record OptionalReasonRequest(@Size(max = 500) String reason) {
}
