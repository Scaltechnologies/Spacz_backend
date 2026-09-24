package com.studyhouse.spacz.partner.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.partner.dto.request.AmenityRequest;
import com.studyhouse.spacz.partner.dto.response.AmenityResponse;
import com.studyhouse.spacz.partner.service.AmenityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/amenities")
@Tag(name = "Amenity APIs", description = "Facilities of a block (AC, Wi-Fi, water, lockers, newspapers).")
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create the amenity record of a block",
            description = "A block can have only one amenity record (409 if it already has one).")
    public ResponseEntity<AmenityResponse> createAmenity(@Valid @RequestBody AmenityRequest request) {
        AmenityResponse created = amenityService.createAmenity(request);
        return ResponseEntity.created(Locations.of(created.amenityId())).body(created);
    }

    @GetMapping
    @Operation(summary = "List all amenity records")
    public List<AmenityResponse> getAllAmenities() {
        return amenityService.getAllAmenities();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an amenity record by ID")
    public AmenityResponse getAmenityById(@PathVariable Long id) {
        return amenityService.getAmenityById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an amenity record")
    public AmenityResponse updateAmenity(@PathVariable Long id, @Valid @RequestBody AmenityRequest request) {
        return amenityService.updateAmenity(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an amenity record")
    public void deleteAmenity(@PathVariable Long id) {
        amenityService.deleteAmenity(id);
    }
}
