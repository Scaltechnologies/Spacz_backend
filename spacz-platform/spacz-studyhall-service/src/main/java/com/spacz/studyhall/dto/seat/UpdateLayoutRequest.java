package com.spacz.studyhall.dto.seat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Replaces all non-seat cells of the block layout; positions left out are EMPTY. Optional: "
        + "seatMoves moves existing seats to other cells (ids, numbers and bookings kept — used to insert/delete rows "
        + "or columns), seatDirections sets the facing of the listed seats, fixtures replaces the edge/centre "
        + "fixtures (null = keep).")
public record UpdateLayoutRequest(@NotNull @Size(max = 3600) List<@Valid @NotNull LayoutFeatureRequest> features,
                                  @Size(max = 3600) List<@Valid @NotNull SeatDirectionRequest> seatDirections,
                                  @Size(max = 3600) List<@Valid @NotNull SeatMoveRequest> seatMoves,
                                  @Size(max = 100) List<@Valid @NotNull FixtureRequest> fixtures) {

    public UpdateLayoutRequest(List<LayoutFeatureRequest> features) {
        this(features, null, null, null);
    }

    public UpdateLayoutRequest(List<LayoutFeatureRequest> features, List<SeatDirectionRequest> seatDirections) {
        this(features, seatDirections, null, null);
    }
}
