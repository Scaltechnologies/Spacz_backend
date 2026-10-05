package com.spacz.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Create / fully replace a platform update")
public record PlatformUpdateRequest(
        @Schema(example = "New study halls in Kukatpally") @NotBlank @Size(max = 120) String title,
        @Schema(example = "Three new study halls are now open for booking.") @NotBlank @Size(max = 2000) String description,
        @Schema(description = "Publish immediately; defaults to false") Boolean published) {
}
