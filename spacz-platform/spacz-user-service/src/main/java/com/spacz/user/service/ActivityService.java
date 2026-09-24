package com.spacz.user.service;

import com.spacz.user.dto.ActivityResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.dto.internal.RecordActivityRequest;
import com.spacz.user.entity.ActivityType;
import org.springframework.data.domain.Pageable;

public interface ActivityService {

    void record(Long userId, ActivityType type, String description, String referenceType, Long referenceId);

    ActivityResponse record(Long userId, RecordActivityRequest request);

    PageResponse<ActivityResponse> list(Long userId, ActivityType type, Pageable pageable);
}
