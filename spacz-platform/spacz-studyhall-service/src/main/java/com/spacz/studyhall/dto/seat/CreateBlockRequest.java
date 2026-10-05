package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Schema(description = """
        Creates a block (section) on a floor, either as a rows × columns grid with a seat in every cell except the
        gaps (numbered <prefix>-<row letter><column>: A1, A2 ... B1, or G-A1 with prefix "G"), or from a
        layoutTemplate (rows, columns and gaps come from the template; seat sN is numbered <prefix>-N).""")
public record CreateBlockRequest(
        @Schema(example = "AC Reading Hall") @NotBlank @Size(max = 80) String name,
        @Schema(example = "5", description = "Required without layoutTemplate") @Min(1) @Max(60) Integer totalRows,
        @Schema(example = "10", description = "Required without layoutTemplate") @Min(1) @Max(60) Integer totalColumns,
        @Schema(example = "G", description = "Optional; needed when another block of the hall uses the same numbers")
        @Size(max = 8) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "may contain letters and digits only")
        String seatNumberPrefix,
        @Schema(description = "Defaults to STANDARD") SeatType defaultSeatType,
        @Schema(description = "Cells with no seat (aisles, pillars)") @Size(max = 3600) List<@Valid @NotNull GridPosition> gaps,
        @Schema(example = "180.00", description = "Overrides the hall's daily price")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal dailyPrice,
        @Schema(example = "3000.00", description = "Overrides the hall's monthly price")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
        @Schema(description = "Block-specific amenities (e.g. AC only in this block)") Set<@NotNull Long> amenityIds,
        @Schema(description = "Floor of this hall; defaults to the first floor (a Ground Floor is created if none)")
        Long floorId,
        @Schema(example = "s1 s2 s3 . . V:Entrance . . s4 s5\nP:Walkway\ns6 s7 s8 . . . . . s9 s10", description = """
                Optional layout template: one line per row, one token per column. '.' empty; s, s12 or s:A01 a seat; \
                P:Walkway or A alone on a line a full-width walkway row; X:<Label> a feature typed by its label \
                (Entrance, Exit, Table, Pillar ...); or a type name (TABLE, PILLAR, AC ...). Any cell token may end \
                with @NORTH, @SOUTH, @EAST, @WEST (@CENTER for AC) or @N/@S/@E/@W/@C (legacy @U/@D/@L/@R accepted): seat facing, entrance/exit \
                opening, AC airflow. Line 1 is row A, the entrance/user side. \
                Replaces totalRows, totalColumns and gaps.""")
        @Size(max = 20000) String layoutTemplate) {
}
