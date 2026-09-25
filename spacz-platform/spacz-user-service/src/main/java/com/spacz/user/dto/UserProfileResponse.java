package com.spacz.user.dto;

import com.spacz.user.entity.StudySlot;

import java.time.Instant;
import java.time.LocalDate;

public record UserProfileResponse(Long id, Long userId, String email, String firstName, String lastName, String phone,
                                  String profileImageUrl, LocalDate dateOfBirth, String city, String state, String bio,
                                  String educationLevel, String preferredCity, StudySlot preferredStudySlot,
                                  Integer dailyStudyHoursGoal, Instant createdAt, Instant updatedAt) {
}
