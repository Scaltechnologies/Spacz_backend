package com.spacz.user.service;

import com.spacz.user.client.dto.EnrollmentDto;

import java.util.List;

/**
 * A student's study-hall memberships, owned by studyhall-service.
 */
public interface EnrollmentQueryService {

    List<EnrollmentDto> enrollments(Long userId);
}
