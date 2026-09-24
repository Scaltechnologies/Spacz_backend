package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.program.ProgramRequest;
import com.spacz.studyhall.dto.program.ProgramResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

/**
 * The exam / course catalog.
 */
public interface ProgramService {

    List<ProgramResponse> listActive(String search, String category);

    PageResponse<ProgramResponse> search(String search, String category, Boolean active, Pageable pageable);

    ProgramResponse get(Long id, boolean includeInactive);

    List<ProgramResponse> lookup(Collection<Long> ids);

    ProgramResponse create(AuthenticatedUser admin, ProgramRequest request);

    ProgramResponse update(AuthenticatedUser admin, Long id, ProgramRequest request);

    /** Programs are never hard-deleted (halls, students and bookings reference them). */
    void deactivate(AuthenticatedUser admin, Long id);
}
