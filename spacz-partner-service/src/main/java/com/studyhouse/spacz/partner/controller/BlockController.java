package com.studyhouse.spacz.partner.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.partner.dto.request.BlockRequest;
import com.studyhouse.spacz.partner.dto.response.AmenityResponse;
import com.studyhouse.spacz.partner.dto.response.BlockResponse;
import com.studyhouse.spacz.partner.dto.response.SeatResponse;
import com.studyhouse.spacz.partner.service.AmenityService;
import com.studyhouse.spacz.partner.service.BlockService;
import com.studyhouse.spacz.partner.service.SeatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/blocks")
@Tag(name = "Block APIs", description = "Halls/sections inside a property, with their own pricing")
public class BlockController {

    private final BlockService blockService;
    private final SeatService seatService;
    private final AmenityService amenityService;

    public BlockController(BlockService blockService, SeatService seatService, AmenityService amenityService) {
        this.blockService = blockService;
        this.seatService = seatService;
        this.amenityService = amenityService;
    }

    @PostMapping
    @Operation(summary = "Create a block inside an existing property", description = "Returns 200 OK (as before).")
    public BlockResponse createBlock(@Valid @RequestBody BlockRequest request) {
        return blockService.createBlock(request);
    }

    @GetMapping
    @Operation(summary = "List all blocks")
    public List<BlockResponse> getAllBlocks() {
        return blockService.getAllBlocks();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a block by ID")
    public BlockResponse getBlockById(@PathVariable Long id) {
        return blockService.getBlockById(id);
    }

    @GetMapping("/{id}/seats")
    @Operation(summary = "List the seats of a block")
    public List<SeatResponse> getBlockSeats(@PathVariable Long id) {
        return seatService.getSeatsByBlockId(id);
    }

    @GetMapping("/{id}/amenity")
    @Operation(summary = "Get the amenity record of a block",
            description = "A block has at most one amenity record. 404 if the block has none yet.")
    public AmenityResponse getBlockAmenity(@PathVariable Long id) {
        return amenityService.getAmenityByBlockId(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a block")
    public BlockResponse updateBlock(@PathVariable Long id, @Valid @RequestBody BlockRequest request) {
        return blockService.updateBlock(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a block", description = "Also deletes the block's seats and amenity record. 204 also for unknown IDs (as before).")
    public void deleteBlock(@PathVariable Long id) {
        blockService.deleteBlock(id);
    }
}
