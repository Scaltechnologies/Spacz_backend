package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.Property;

public interface PropertyService {
    Property saveProperty(Property property);
    List<Property> getAllProperties();
    Optional<Property> getPropertyById(Long id);
    Property updateProperty(Long id, Property property);
    void deleteProperty(Long id);
}