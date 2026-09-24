package com.spacz.user.service.impl;

import com.spacz.user.client.StudyHallServiceClient;
import com.spacz.user.client.dto.EnrollmentDto;
import com.spacz.user.exception.ResourceNotFoundException;
import com.spacz.user.repository.UserProfileRepository;
import com.spacz.user.service.EnrollmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentQueryServiceImpl implements EnrollmentQueryService {

    private final UserProfileRepository profileRepository;
    private final StudyHallServiceClient studyHallClient;

    @Override
    public List<EnrollmentDto> enrollments(Long userId) {
        if (!profileRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException("User profile", userId);
        }
        return studyHallClient.enrollments(userId);
    }
}
