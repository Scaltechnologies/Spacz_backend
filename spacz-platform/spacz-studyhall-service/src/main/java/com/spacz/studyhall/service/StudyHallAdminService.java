package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHallStatus;
import org.springframework.data.domain.Pageable;

public interface StudyHallAdminService {

    PageResponse<StudyHallSummaryResponse> search(String search, StudyHallStatus status, String city, Long vendorId,
                                                  Pageable pageable);

    StudyHallDetailResponse get(Long studyHallId);

    StudyHallDetailResponse applyAction(Long studyHallId, StatusAction action, String reason);
}
