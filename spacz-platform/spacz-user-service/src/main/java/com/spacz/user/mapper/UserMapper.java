package com.spacz.user.mapper;

import com.spacz.user.dto.ActivityResponse;
import com.spacz.user.dto.UserProfileResponse;
import com.spacz.user.dto.UserProgramResponse;
import com.spacz.user.dto.internal.UserSummaryResponse;
import com.spacz.user.entity.UserActivity;
import com.spacz.user.entity.UserProfile;
import com.spacz.user.entity.UserProgram;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(p.getId(), p.getUserId(), p.getEmail(), p.getFirstName(), p.getLastName(),
                p.getPhone(), p.getProfileImageUrl(), p.getDateOfBirth(), p.getCity(), p.getState(), p.getBio(),
                p.getEducationLevel(), p.getPreferredCity(), p.getPreferredStudySlot(), p.getDailyStudyHoursGoal(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public UserSummaryResponse toSummary(UserProfile p) {
        return new UserSummaryResponse(p.getUserId(), p.getFirstName(), p.getLastName(), p.getEmail(), p.getPhone(),
                p.getCity());
    }

    public UserProgramResponse toResponse(UserProgram p) {
        return new UserProgramResponse(p.getId(), p.getUserId(), p.getProgramId(), p.getProgramCode(),
                p.getProgramName(), p.getStartDate(), p.getTargetDate(), p.getStatus(), p.getNotes(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public ActivityResponse toResponse(UserActivity activity) {
        return new ActivityResponse(activity.getId(), activity.getType(), activity.getDescription(),
                activity.getReferenceType(), activity.getReferenceId(), activity.getCreatedAt());
    }
}
