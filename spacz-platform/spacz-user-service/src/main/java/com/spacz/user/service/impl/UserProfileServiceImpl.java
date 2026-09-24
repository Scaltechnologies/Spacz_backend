package com.spacz.user.service.impl;

import com.spacz.user.dto.UpdateUserProfileRequest;
import com.spacz.user.dto.UserProfileResponse;
import com.spacz.user.dto.internal.CreateUserProfileRequest;
import com.spacz.user.dto.internal.UserSummaryResponse;
import com.spacz.user.entity.ActivityType;
import com.spacz.user.entity.UserProfile;
import com.spacz.user.exception.ResourceNotFoundException;
import com.spacz.user.mapper.UserMapper;
import com.spacz.user.repository.UserProfileRepository;
import com.spacz.user.service.ActivityService;
import com.spacz.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository profileRepository;
    private final ActivityService activityService;
    private final UserMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return mapper.toResponse(find(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        UserProfile profile = find(userId);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(trimToNull(request.lastName()));
        profile.setPhone(trimToNull(request.phone()));
        profile.setProfileImageUrl(trimToNull(request.profileImageUrl()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setCity(trimToNull(request.city()));
        profile.setState(trimToNull(request.state()));
        profile.setBio(trimToNull(request.bio()));
        profile.setEducationLevel(trimToNull(request.educationLevel()));
        profile.setPreferredCity(trimToNull(request.preferredCity()));
        profile.setPreferredStudySlot(request.preferredStudySlot());
        profile.setDailyStudyHoursGoal(request.dailyStudyHoursGoal());
        activityService.record(userId, ActivityType.PROFILE_UPDATED, "Profile updated", "USER_PROFILE", profile.getId());
        return mapper.toResponse(profileRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public CreationResult createProfile(CreateUserProfileRequest request) {
        Optional<UserProfile> existing = profileRepository.findByUserId(request.userId());
        if (existing.isPresent()) {
            return new CreationResult(mapper.toResponse(existing.get()), false);
        }
        UserProfile profile = UserProfile.create(request.userId(), request.email(), request.firstName());
        profile.setLastName(request.lastName());
        profile.setPhone(request.phone());
        profile.setCity(request.city());
        profile.setPreferredCity(request.city());
        UserProfile saved = profileRepository.saveAndFlush(profile);
        activityService.record(request.userId(), ActivityType.PROFILE_CREATED, "Welcome to SPACZ", "USER_PROFILE",
                saved.getId());
        return new CreationResult(mapper.toResponse(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getSummaries(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return profileRepository.findByUserIdIn(userIds).stream().map(mapper::toSummary).toList();
    }

    private UserProfile find(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile", userId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
