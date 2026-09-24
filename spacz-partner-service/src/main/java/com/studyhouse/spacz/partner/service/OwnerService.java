package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.OwnerRequest;
import com.studyhouse.spacz.partner.dto.response.OwnerResponse;

public interface OwnerService {

    OwnerResponse createOwner(OwnerRequest request);

    List<OwnerResponse> getAllOwners();

    OwnerResponse getOwnerById(Long id);

    OwnerResponse updateOwner(Long id, OwnerRequest request);

    void deleteOwner(Long id);
}
