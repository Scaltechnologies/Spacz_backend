package com.spacz.user.dto.internal;

import com.spacz.user.entity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordActivityRequest(
        @NotNull ActivityType type,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 40) String referenceType,
        Long referenceId) {
}
