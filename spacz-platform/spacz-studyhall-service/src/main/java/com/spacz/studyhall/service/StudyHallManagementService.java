package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.hall.ImageRequest;
import com.spacz.studyhall.dto.hall.OperatingHoursRequest;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.hall.UpdateStudyHallRequest;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * A vendor managing their own study halls. The vendor always comes from the JWT.
 */
public interface StudyHallManagementService {

    StudyHallDetailResponse create(AuthenticatedUser vendor, CreateStudyHallRequest request);

    PageResponse<StudyHallSummaryResponse> listMine(Long vendorId, StudyHallStatus status, Pageable pageable);

    StudyHallDetailResponse update(AuthenticatedUser vendor, Long studyHallId, UpdateStudyHallRequest request);

    void delete(AuthenticatedUser vendor, Long studyHallId);

    StudyHallDetailResponse submitForApproval(AuthenticatedUser vendor, Long studyHallId);

    StudyHallDetailResponse changeVisibility(Long vendorId, Long studyHallId, StudyHallStatus status);

    StudyHallDetailResponse replaceOperatingHours(Long vendorId, Long studyHallId, OperatingHoursRequest request);

    StudyHallDetailResponse addImage(Long vendorId, Long studyHallId, ImageRequest request);

    StudyHallDetailResponse removeImage(Long vendorId, Long studyHallId, Long imageId);

    StudyHallDetailResponse addAmenities(Long vendorId, Long studyHallId, Set<Long> amenityIds);

    StudyHallDetailResponse removeAmenity(Long vendorId, Long studyHallId, Long amenityId);

    StudyHallDetailResponse addPrograms(Long vendorId, Long studyHallId, Set<Long> programIds);

    StudyHallDetailResponse removeProgram(Long vendorId, Long studyHallId, Long programId);
}
