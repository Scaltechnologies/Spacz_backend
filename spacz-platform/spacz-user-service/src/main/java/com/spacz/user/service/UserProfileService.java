package com.spacz.user.service;

import com.spacz.user.dto.UpdateUserProfileRequest;
import com.spacz.user.dto.UserProfileResponse;
import com.spacz.user.dto.internal.CreateUserProfileRequest;
import com.spacz.user.dto.internal.UserSummaryResponse;

import java.util.Collection;
import java.util.List;

public interface UserProfileService {

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request);

    /**
     * Idempotent: returns the existing profile if one exists for the userId.
     *
     * @return the profile and whether it was created by this call
     */
    CreationResult createProfile(CreateUserProfileRequest request);

    List<UserSummaryResponse> getSummaries(Collection<Long> userIds);

    record CreationResult(UserProfileResponse profile, boolean created) {
    }
}
