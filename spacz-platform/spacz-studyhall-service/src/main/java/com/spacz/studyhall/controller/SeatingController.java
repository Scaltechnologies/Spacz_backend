package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.CreateSeatRequest;
import com.spacz.studyhall.dto.seat.SeatMapResponse;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.dto.seat.UpdateSeatRequest;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.StudyHallQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/studyhalls/{studyHallId}")
@RequiredArgsConstructor
@Tag(name = "Seating", description = "Blocks (sections laid out as grids with gaps) and seats")
@ApiErrorResponses
public class SeatingController {

    private final SeatingService seatingService;
    private final StudyHallQueryService queryService;

    @GetMapping("/seats")
    @Operation(summary = "Seat map with per-seat availability for a date range (defaults to today)",
            description = "Public for live halls; the owner sees it in any status.")
    public SeatMapResponse seats(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long studyHallId,
                                 @Parameter(example = "2026-10-01") @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @Parameter(example = "2026-10-31") @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return queryService.seatMap(studyHallId, caller, startDate, endDate);
    }

    @PostMapping("/seats")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Add a seat in an empty cell of a block", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public SeatResponse createSeat(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                   @Valid @RequestBody CreateSeatRequest request) {
        return seatingService.createSeat(vendor.userId(), studyHallId, request);
    }

    @PutMapping("/seats/{seatId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Update a seat (number, type, status, price overrides)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public SeatResponse updateSeat(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                   @PathVariable Long seatId, @Valid @RequestBody UpdateSeatRequest request) {
        return seatingService.updateSeat(vendor.userId(), studyHallId, seatId, request);
    }

    @DeleteMapping("/seats/{seatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Delete a seat that has never been booked", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public void deleteSeat(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                           @PathVariable Long seatId) {
        seatingService.deleteSeat(vendor.userId(), studyHallId, seatId);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Create a block and generate its seats",
            description = "Example: 5 rows × 10 columns with gaps [{row:1,column:5},{row:2,column:5}] for an aisle.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public BlockResponse createBlock(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                     @Valid @RequestBody CreateBlockRequest request) {
        return seatingService.createBlock(vendor.userId(), studyHallId, request);
    }

    @GetMapping("/blocks")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "All blocks with their seats", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public List<BlockResponse> blocks(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId) {
        return seatingService.listBlocks(vendor.userId(), studyHallId);
    }

    @PutMapping("/blocks/{blockId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Update a block (name, grid size, prices, amenities)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public BlockResponse updateBlock(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                     @PathVariable Long blockId, @Valid @RequestBody UpdateBlockRequest request) {
        return seatingService.updateBlock(vendor.userId(), studyHallId, blockId, request);
    }

    @DeleteMapping("/blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Delete a block that has never been booked", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public void deleteBlock(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                            @PathVariable Long blockId) {
        seatingService.deleteBlock(vendor.userId(), studyHallId, blockId);
    }
}
