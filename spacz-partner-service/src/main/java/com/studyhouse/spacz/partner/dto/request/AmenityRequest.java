package com.studyhouse.spacz.partner.dto.request;

import com.studyhouse.spacz.partner.dto.BlockRef;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Amenities of a block (one record per block). Same fields as the legacy API; flags "
        + "not sent are false. `block` is required on POST and must exist; on PUT it is optional "
        + "(omitted = keep the current block).")
public record AmenityRequest(
        @Schema(example = "true") Boolean ac,
        @Schema(example = "true") Boolean wifi,
        @Schema(example = "true") Boolean water,
        @Schema(example = "false") Boolean lockers,
        @Schema(example = "false") Boolean newspapers,
        BlockRef block) {

    public Long blockId() {
        return block == null ? null : block.blockId();
    }
}
