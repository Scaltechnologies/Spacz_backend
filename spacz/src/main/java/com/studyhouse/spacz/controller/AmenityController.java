package com.studyhouse.spacz.controller;
import com.studyhouse.spacz.entity.Amenity;
import com.studyhouse.spacz.service.AmenityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/amenities")
public class AmenityController {

    private final AmenityService amenityService;

    @Autowired
    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    // Get all amenities
    @GetMapping
    public ResponseEntity<List<Amenity>> getAllAmenities() {
        List<Amenity> amenities = amenityService.getAllAmenities();
        return new ResponseEntity<>(amenities, HttpStatus.OK);
    }

    // Get a single amenity by ID
    @GetMapping("/{id}")
    public ResponseEntity<Amenity> getAmenityById(@PathVariable("id") Long id) {
        Optional<Amenity> amenity = amenityService.getAmenityById(id);
        return amenity.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // Create a new amenity
    @PostMapping
    public ResponseEntity<Amenity> createAmenity(@RequestBody Amenity amenity) {
        Amenity savedAmenity = amenityService.saveAmenity(amenity);
        return new ResponseEntity<>(savedAmenity, HttpStatus.CREATED);
    }

    // Update an existing amenity
    @PutMapping("/{id}")
    public ResponseEntity<Amenity> updateAmenity(@PathVariable("id") Long id, @RequestBody Amenity amenity) {
        Amenity updatedAmenity = amenityService.updateAmenity(id, amenity);
        return updatedAmenity != null ? new ResponseEntity<>(updatedAmenity, HttpStatus.OK) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // Delete an amenity by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAmenity(@PathVariable("id") Long id) {
        if (amenityService.deleteAmenity(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}