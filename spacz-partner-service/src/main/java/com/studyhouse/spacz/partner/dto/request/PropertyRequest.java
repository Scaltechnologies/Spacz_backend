package com.studyhouse.spacz.partner.dto.request;

import com.studyhouse.spacz.partner.dto.OwnerRef;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Property. Same fields as the legacy API. `owner` is required on POST and must exist; "
        + "on PUT it is optional (omitted = keep the current owner).")
public record PropertyRequest(
        @Schema(example = "Green Valley Study Center") @Size(max = 255) String propertyName,
        @Schema(example = "Plot 4, Ameerpet, Hyderabad") @Size(max = 255) String address,
        @Schema(example = "17.4375,78.4483") @Size(max = 255) String googleCoordinates,
        OwnerRef owner) {

    public Long ownerId() {
        return owner == null ? null : owner.ownerId();
    }
}
