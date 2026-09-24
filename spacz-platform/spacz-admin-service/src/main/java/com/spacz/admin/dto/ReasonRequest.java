package com.spacz.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Required reason (reject / suspend). Shown to the vendor and stored in the audit log.")
public record ReasonRequest(@Schema(example = "Incomplete KYC documents") @NotBlank @Size(max = 500) String reason) {
}
