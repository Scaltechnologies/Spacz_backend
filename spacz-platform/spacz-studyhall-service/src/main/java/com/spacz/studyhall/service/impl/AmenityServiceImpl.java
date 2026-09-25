package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.audit.AuditEvents;
import com.spacz.studyhall.dto.hall.AmenityRequest;
import com.spacz.studyhall.dto.hall.AmenityResponse;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.mapper.StudyHallMapper;
import com.spacz.studyhall.repository.AmenityRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.AmenityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final StudyHallMapper mapper;
    private final AuditEvents auditEvents;

    @Override
    @Transactional(readOnly = true)
    public List<AmenityResponse> listActive() {
        return amenityRepository.findByActiveTrueOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AmenityResponse> listAll() {
        return amenityRepository.findAll(Sort.by("name")).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AmenityResponse create(AuthenticatedUser admin, AmenityRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (amenityRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("An amenity with code " + code + " already exists");
        }
        if (amenityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("An amenity named " + name + " already exists");
        }
        Amenity amenity = Amenity.create(code, name, request.icon(), request.description());
        if (request.active() != null) {
            amenity.setActive(request.active());
        }
        Amenity saved = amenityRepository.saveAndFlush(amenity);
        auditEvents.record(admin, "AMENITY_CREATED", "AMENITY", saved.getId(), "Created amenity " + code);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AmenityResponse update(AuthenticatedUser admin, Long id, AmenityRequest request) {
        Amenity amenity = amenityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Amenity", id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (amenityRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("An amenity with code " + code + " already exists");
        }
        if (amenityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("An amenity named " + name + " already exists");
        }
        amenity.setCode(code);
        amenity.setName(name);
        amenity.setIcon(request.icon());
        amenity.setDescription(request.description());
        if (request.active() != null) {
            amenity.setActive(request.active());
        }
        auditEvents.record(admin, "AMENITY_UPDATED", "AMENITY", id, "Updated amenity " + code);
        return mapper.toResponse(amenityRepository.saveAndFlush(amenity));
    }
}
