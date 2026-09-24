package com.studyhouse.spacz.partner.dto.request;

import com.studyhouse.spacz.partner.dto.PropertyRef;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Property image (URL only). Same fields as the legacy API. `property` is required on POST "
        + "and must exist; on PUT it is optional (omitted = keep the current property).")
public record ImageRequest(
        @Schema(example = "https://cdn.example.com/spacz/property-1/front.jpg") @Size(max = 255) String imageUrl,
        PropertyRef property) {

    public Long propertyId() {
        return property == null ? null : property.propertyId();
    }
}
