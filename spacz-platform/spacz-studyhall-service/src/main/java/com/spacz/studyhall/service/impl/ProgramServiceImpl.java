package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.audit.AuditEvents;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.program.ProgramRequest;
import com.spacz.studyhall.dto.program.ProgramResponse;
import com.spacz.studyhall.entity.Program;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.mapper.StudyHallMapper;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.ProgramSpecifications;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramRepository programRepository;
    private final StudyHallMapper mapper;
    private final AuditEvents auditEvents;

    @Override
    @Transactional(readOnly = true)
    public List<ProgramResponse> listActive(String search, String category) {
        return programRepository.findAll(ProgramSpecifications.matching(search, category, true),
                Sort.by("category", "name")).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> search(String search, String category, Boolean active, Pageable pageable) {
        return PageResponse.from(programRepository.findAll(ProgramSpecifications.matching(search, category, active),
                pageable), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponse get(Long id, boolean includeInactive) {
        return programRepository.findById(id)
                .filter(p -> includeInactive || p.isActive())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Program", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramResponse> lookup(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : programRepository.findByIdIn(ids).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ProgramResponse create(AuthenticatedUser admin, ProgramRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (programRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("A program with code " + code + " already exists");
        }
        if (programRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A program named " + name + " already exists");
        }
        Program program = Program.create(code, name, request.description(), request.category().trim());
        if (request.active() != null) {
            program.setActive(request.active());
        }
        Program saved = programRepository.saveAndFlush(program);
        auditEvents.record(admin, "PROGRAM_CREATED", "PROGRAM", saved.getId(), "Created program " + code + " (" + name + ")");
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProgramResponse update(AuthenticatedUser admin, Long id, ProgramRequest request) {
        Program program = find(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (programRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("A program with code " + code + " already exists");
        }
        if (programRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("A program named " + name + " already exists");
        }
        program.setCode(code);
        program.setName(name);
        program.setDescription(request.description());
        program.setCategory(request.category().trim());
        if (request.active() != null) {
            program.setActive(request.active());
        }
        auditEvents.record(admin, "PROGRAM_UPDATED", "PROGRAM", id, "Updated program " + code);
        return mapper.toResponse(programRepository.saveAndFlush(program));
    }

    @Override
    @Transactional
    public void deactivate(AuthenticatedUser admin, Long id) {
        Program program = find(id);
        if (program.isActive()) {
            program.setActive(false);
            auditEvents.record(admin, "PROGRAM_DEACTIVATED", "PROGRAM", id, "Deactivated program " + program.getCode());
        }
    }

    private Program find(Long id) {
        return programRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Program", id));
    }
}
