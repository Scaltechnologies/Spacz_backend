package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Changes one photo. Fields left out (null) are unchanged.")
public record ImageUpdateRequest(
        @Schema(description = "true makes this the cover (the previous cover is unset); false is ignored, "
                + "since a hall with photos always has exactly one cover") Boolean cover,
        @Schema(description = "New caption; an empty string clears it") @Size(max = 200) String caption) {
}
