package com.studyhouse.spacz.service;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.Amenity;
import com.studyhouse.spacz.repository.AmenityRepository;

@Service
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;

    @Autowired
    public AmenityServiceImpl(AmenityRepository amenityRepository) {
        this.amenityRepository = amenityRepository;
    }

    @Override
    public List<Amenity> getAllAmenities() {
        return amenityRepository.findAll();
    }

    @Override
    public Optional<Amenity> getAmenityById(Long id) {
        return amenityRepository.findById(id);
    }

    @Override
    public Amenity saveAmenity(Amenity amenity) {
        return amenityRepository.save(amenity);
    }

    @Override
    public Amenity updateAmenity(Long id, Amenity amenity) {
        if (amenityRepository.existsById(id)) {
            amenity.setAmenityId(id);
            return amenityRepository.save(amenity);
        }
        return null; // or throw a custom exception
    }

    @Override
    public boolean deleteAmenity(Long id) {
        if (amenityRepository.existsById(id)) {
            amenityRepository.deleteById(id);
            return true;
        }
        return false;
    }
}