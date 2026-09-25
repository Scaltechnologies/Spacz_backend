package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.StudyHallSpecifications;
import com.spacz.studyhall.service.StudyHallAdminService;
import com.spacz.studyhall.service.support.PageableSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyHallAdminServiceImpl implements StudyHallAdminService {

    private static final Set<String> SORTABLE = Set.of("name", "city", "createdAt", "updatedAt", "submittedAt",
            "pricePerDay", "status");

    private final StudyHallRepository studyHallRepository;
    private final HallViewAssembler assembler;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudyHallSummaryResponse> search(String search, StudyHallStatus status, String city,
                                                         Long vendorId, Pageable pageable) {
        Specification<StudyHall> spec = Specification.where(StudyHallSpecifications.textMatches(search))
                .and(StudyHallSpecifications.hasStatus(status))
                .and(StudyHallSpecifications.inCity(city))
                .and(StudyHallSpecifications.ownedByVendor(vendorId));
        return assembler.page(studyHallRepository.findAll(spec,
                PageableSupport.restrictSort(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"))), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public StudyHallDetailResponse get(Long studyHallId) {
        return assembler.detail(find(studyHallId));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse applyAction(Long studyHallId, StatusAction action, String reason) {
        StudyHall hall = find(studyHallId);
        hall.apply(action, reason == null || reason.isBlank() ? null : reason.trim(), clock.instant());
        studyHallRepository.saveAndFlush(hall);
        log.info("Study hall {} -> {} ({})", studyHallId, hall.getStatus(), action);
        return assembler.detail(hall);
    }

    private StudyHall find(Long studyHallId) {
        return studyHallRepository.findWithVendor(studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Study hall", studyHallId));
    }
}
