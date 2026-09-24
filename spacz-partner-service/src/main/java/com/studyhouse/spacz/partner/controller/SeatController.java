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

import com.studyhouse.spacz.partner.dto.request.SeatRequest;
import com.studyhouse.spacz.partner.dto.response.SeatResponse;
import com.studyhouse.spacz.partner.service.SeatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/seats")
@Tag(name = "Seat APIs", description = "Individual seats inside a block")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    @Operation(summary = "Create a seat inside an existing block", description = "Returns 200 OK (as before).")
    public SeatResponse createSeat(@Valid @RequestBody SeatRequest request) {
        return seatService.createSeat(request);
    }

    @GetMapping
    @Operation(summary = "List all seats")
    public List<SeatResponse> getAllSeats() {
        return seatService.getAllSeats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a seat by ID")
    public SeatResponse getSeatById(@PathVariable Long id) {
        return seatService.getSeatById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a seat")
    public SeatResponse updateSeat(@PathVariable Long id, @Valid @RequestBody SeatRequest request) {
        return seatService.updateSeat(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a seat",
            description = "204 also for unknown IDs (as before). 409 if the seat is still referenced by a booking.")
    public void deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
    }
}
