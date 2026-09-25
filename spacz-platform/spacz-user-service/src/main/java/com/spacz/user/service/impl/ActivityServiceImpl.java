package com.spacz.user.service.impl;

import com.spacz.user.dto.ActivityResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.dto.internal.RecordActivityRequest;
import com.spacz.user.entity.ActivityType;
import com.spacz.user.entity.UserActivity;
import com.spacz.user.mapper.UserMapper;
import com.spacz.user.repository.UserActivityRepository;
import com.spacz.user.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final UserActivityRepository activityRepository;
    private final UserMapper mapper;

    @Override
    @Transactional
    public void record(Long userId, ActivityType type, String description, String referenceType, Long referenceId) {
        activityRepository.save(UserActivity.of(userId, type, description, referenceType, referenceId));
    }

    @Override
    @Transactional
    public ActivityResponse record(Long userId, RecordActivityRequest request) {
        return mapper.toResponse(activityRepository.save(UserActivity.of(userId, request.type(),
                request.description(), request.referenceType(), request.referenceId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> list(Long userId, ActivityType type, Pageable pageable) {
        Page<UserActivity> page = type == null
                ? activityRepository.findByUserId(userId, pageable)
                : activityRepository.findByUserIdAndType(userId, type, pageable);
        return PageResponse.from(page, mapper::toResponse);
    }
}
