package com.studyhouse.spacz.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Parent reference in request bodies, e.g. {@code "block": {"blockId": 1}} (same as the legacy API). */
@Schema(description = "Reference to an existing block")
public record BlockRef(@Schema(example = "1") Long blockId) {
}
