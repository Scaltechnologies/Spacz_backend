package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.AmenityRequest;
import com.studyhouse.spacz.partner.dto.response.AmenityResponse;

public interface AmenityService {

    AmenityResponse createAmenity(AmenityRequest request);

    List<AmenityResponse> getAllAmenities();

    AmenityResponse getAmenityById(Long id);

    AmenityResponse getAmenityByBlockId(Long blockId);

    AmenityResponse updateAmenity(Long id, AmenityRequest request);

    void deleteAmenity(Long id);
}
