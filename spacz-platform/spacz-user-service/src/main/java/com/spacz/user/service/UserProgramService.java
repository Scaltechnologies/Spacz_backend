package com.spacz.user.service;

import com.spacz.user.dto.UserProgramRequest;
import com.spacz.user.dto.UserProgramResponse;

import java.util.List;

/**
 * The exams / courses a student is preparing for.
 */
public interface UserProgramService {

    List<UserProgramResponse> list(Long userId);

    UserProgramResponse add(Long userId, UserProgramRequest request);

    UserProgramResponse update(Long userId, Long userProgramId, UserProgramRequest request);

    void remove(Long userId, Long userProgramId);
}
