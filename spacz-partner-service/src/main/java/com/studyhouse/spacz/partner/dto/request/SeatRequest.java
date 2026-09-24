package com.studyhouse.spacz.partner.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.studyhouse.spacz.partner.dto.BlockRef;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Seat. Same fields as the legacy API. `block` is required on POST and must exist; "
        + "on PUT it is optional (omitted = keep the current block).")
public record SeatRequest(
        @Schema(example = "A1") @Size(max = 255) String seatNumber,
        BlockRef block,
        @Schema(description = "`isReserved` is accepted too", example = "false") @JsonAlias("isReserved") Boolean reserved,
        @Schema(example = "200.0") Double seatPrice) {

    public Long blockId() {
        return block == null ? null : block.blockId();
    }
}
