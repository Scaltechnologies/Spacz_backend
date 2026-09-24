package com.studyhouse.spacz.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Parent reference in request bodies, e.g. {@code "property": {"propertyId": 1}} (same as the legacy API). */
@Schema(description = "Reference to an existing property")
public record PropertyRef(@Schema(example = "1") Long propertyId) {
}
