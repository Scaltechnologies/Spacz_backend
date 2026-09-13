package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.Amenity;

public interface AmenityService {
    Amenity saveAmenity(Amenity amenity);
    List<Amenity> getAllAmenities();
    Optional<Amenity> getAmenityById(Long id);
    Amenity updateAmenity(Long id, Amenity amenity);
    boolean deleteAmenity(Long id);
}