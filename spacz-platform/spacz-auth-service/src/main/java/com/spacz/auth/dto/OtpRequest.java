package com.spacz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequest(@Schema(example = "9876543210", description = "National or international format")
                         @NotBlank @Size(max = 20) String phone) {
}
