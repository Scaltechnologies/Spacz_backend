package com.studyhouse.spacz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.entity.Amenity;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, Long> {
}