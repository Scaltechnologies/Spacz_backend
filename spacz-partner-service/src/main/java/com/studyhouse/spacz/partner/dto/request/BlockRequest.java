package com.studyhouse.spacz.partner.dto.request;

import com.studyhouse.spacz.partner.dto.PropertyRef;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Block. Same fields as the legacy API. `property` is required on POST and must exist; "
        + "on PUT it is optional (omitted = keep the current property). Prices default to 0 on create.")
public record BlockRequest(
        @Schema(example = "AC Reading Hall") @Size(max = 255) String blockName,
        PropertyRef property,
        @Schema(example = "150.0") Double blockDailyPrice,
        @Schema(example = "2500.0") Double blockMonthlyPrice) {

    public Long propertyId() {
        return property == null ? null : property.propertyId();
    }
}
