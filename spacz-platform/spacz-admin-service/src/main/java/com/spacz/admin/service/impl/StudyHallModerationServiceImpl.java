package com.spacz.admin.service.impl;

import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.dto.StatusActionDto;
import com.spacz.admin.client.dto.StudyHallDetailDto;
import com.spacz.admin.client.dto.StudyHallSummaryDto;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.service.StudyHallModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyHallModerationServiceImpl implements StudyHallModerationService {

    private static final String ENTITY = "STUDY_HALL";

    private final StudyHallServiceClient studyHallClient;
    private final AuditService auditService;

    @Override
    public PageResponse<StudyHallSummaryDto> search(String search, String status, String city, Long vendorId,
                                                    Pageable pageable) {
        return studyHallClient.searchStudyHalls(search, status, city, vendorId, pageable);
    }

    @Override
    public StudyHallDetailDto get(Long id) {
        return studyHallClient.getStudyHall(id);
    }

    @Override
    public StudyHallDetailDto approve(AuthenticatedUser admin, Long id, String note) {
        return act(admin, id, "APPROVE", note, AuditAction.STUDY_HALL_APPROVED, "Approved study hall");
    }

    @Override
    public StudyHallDetailDto reject(AuthenticatedUser admin, Long id, String reason) {
        return act(admin, id, "REJECT", reason, AuditAction.STUDY_HALL_REJECTED, "Rejected study hall");
    }

    @Override
    public StudyHallDetailDto suspend(AuthenticatedUser admin, Long id, String reason) {
        return act(admin, id, "SUSPEND", reason, AuditAction.STUDY_HALL_SUSPENDED, "Suspended study hall");
    }

    @Override
    public StudyHallDetailDto activate(AuthenticatedUser admin, Long id, String note) {
        return act(admin, id, "ACTIVATE", note, AuditAction.STUDY_HALL_ACTIVATED, "Activated study hall");
    }

    private StudyHallDetailDto act(AuthenticatedUser admin, Long id, String action, String reason,
                                   AuditAction auditAction, String verb) {
        StudyHallDetailDto result = studyHallClient.changeStudyHallStatus(id, new StatusActionDto(action, reason));
        auditService.record(admin, auditAction, ENTITY, id,
                verb + " '" + result.name() + "' (" + result.city() + ")"
                        + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        return result;
    }
}
