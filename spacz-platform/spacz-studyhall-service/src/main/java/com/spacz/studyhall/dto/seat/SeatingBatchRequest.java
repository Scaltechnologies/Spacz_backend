package com.spacz.studyhall.dto.seat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Applies a whole seating-plan edit (floors, blocks, layouts and seats) in ONE database "
        + "transaction: either every operation succeeds or nothing changes. Operations run in the order given, each "
        + "with the same rules as its single route (DELETE_BLOCK = DELETE /blocks/{id}, CREATE_SEAT = POST /seats ...). "
        + "A CREATE_FLOOR or CREATE_BLOCK may carry a `ref`; later operations point at the new floor or block with "
        + "`floorRef` / `blockRef` instead of an id. The created ids are returned by ref.")
public record SeatingBatchRequest(@NotEmpty @Size(max = 5000) List<@Valid @NotNull Operation> operations) {

    public enum Type {
        DELETE_SEAT, DELETE_BLOCK, DELETE_FLOOR, CREATE_FLOOR, UPDATE_FLOOR, CREATE_BLOCK, UPDATE_BLOCK,
        UPDATE_LAYOUT, CREATE_SEAT
    }

    @Schema(description = "One step. Set the fields its type needs; the rest are ignored.")
    public record Operation(
            @NotNull Type type,
            @Schema(description = "CREATE_FLOOR / CREATE_BLOCK: a name for the new id")
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$") String ref,
            @Schema(description = "UPDATE_FLOOR, DELETE_FLOOR; target floor of CREATE_BLOCK / UPDATE_BLOCK") Long floorId,
            @Schema(description = "A floor created earlier in this batch")
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$") String floorRef,
            @Schema(description = "UPDATE_BLOCK, DELETE_BLOCK, UPDATE_LAYOUT") Long blockId,
            @Schema(description = "A block created earlier in this batch (UPDATE_LAYOUT)")
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$") String blockRef,
            @Schema(description = "DELETE_SEAT") Long seatId,
            @Schema(description = "CREATE_FLOOR, UPDATE_FLOOR") @Valid FloorRequest floor,
            @Schema(description = "CREATE_BLOCK (its floorId is replaced by floorId/floorRef when given)")
            @Valid CreateBlockRequest createBlock,
            @Schema(description = "UPDATE_BLOCK (its floorId is replaced by floorId/floorRef when given)")
            @Valid UpdateBlockRequest updateBlock,
            @Schema(description = "UPDATE_LAYOUT") @Valid UpdateLayoutRequest layout,
            @Schema(description = "CREATE_SEAT") @Valid CreateSeatRequest seat) {
    }
}
