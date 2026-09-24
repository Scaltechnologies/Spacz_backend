package com.spacz.auth.dto;

import com.spacz.auth.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateRequest(@NotNull AccountStatus status) {
}
