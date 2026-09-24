package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.PropertyRequest;
import com.studyhouse.spacz.partner.dto.response.PropertyResponse;

public interface PropertyService {

    PropertyResponse createProperty(PropertyRequest request);

    List<PropertyResponse> getAllProperties();

    PropertyResponse getPropertyById(Long id);

    List<PropertyResponse> getPropertiesByOwnerId(Long ownerId);

    PropertyResponse updateProperty(Long id, PropertyRequest request);

    void deleteProperty(Long id);
}
