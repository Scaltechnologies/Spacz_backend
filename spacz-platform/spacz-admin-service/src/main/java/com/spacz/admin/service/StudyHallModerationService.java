package com.spacz.admin.service;

import com.spacz.admin.client.dto.StudyHallDetailDto;
import com.spacz.admin.client.dto.StudyHallSummaryDto;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

public interface StudyHallModerationService {

    PageResponse<StudyHallSummaryDto> search(String search, String status, String city, Long vendorId, Pageable pageable);

    StudyHallDetailDto get(Long id);

    StudyHallDetailDto approve(AuthenticatedUser admin, Long id, String note);

    StudyHallDetailDto reject(AuthenticatedUser admin, Long id, String reason);

    StudyHallDetailDto suspend(AuthenticatedUser admin, Long id, String reason);

    StudyHallDetailDto activate(AuthenticatedUser admin, Long id, String note);
}
