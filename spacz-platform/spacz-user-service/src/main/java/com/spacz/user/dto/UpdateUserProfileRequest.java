package com.spacz.user.dto;

import com.spacz.user.entity.StudySlot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

@Schema(description = "Full replacement of the editable profile fields. Email is managed by auth-service.")
public record UpdateUserProfileRequest(
        @Schema(example = "Asha") @NotBlank @Size(max = 80) String firstName,
        @Schema(example = "Rao") @Size(max = 80) String lastName,
        @Schema(example = "+919876543210") @Pattern(regexp = "^\\+?[0-9][0-9 -]{6,18}$",
                message = "must be a valid phone number") String phone,
        @URL @Size(max = 500) String profileImageUrl,
        @Past LocalDate dateOfBirth,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Size(max = 500) String bio,
        @Schema(example = "B.Tech") @Size(max = 80) String educationLevel,
        @Schema(description = "City where the student wants to find study halls") @Size(max = 80) String preferredCity,
        StudySlot preferredStudySlot,
        @Min(1) @Max(16) Integer dailyStudyHoursGoal) {
}
