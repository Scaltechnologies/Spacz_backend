package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.hall.AmenityRequest;
import com.spacz.studyhall.dto.hall.AmenityResponse;
import com.spacz.studyhall.security.AuthenticatedUser;

import java.util.List;

public interface AmenityService {

    List<AmenityResponse> listActive();

    List<AmenityResponse> listAll();

    AmenityResponse create(AuthenticatedUser admin, AmenityRequest request);

    AmenityResponse update(AuthenticatedUser admin, Long id, AmenityRequest request);
}
