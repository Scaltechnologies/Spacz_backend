package com.spacz.admin.client.dto;

import java.time.Instant;
import java.time.LocalDate;

public record UserProfileDto(Long id, Long userId, String email, String firstName, String lastName, String phone,
                             String profileImageUrl, LocalDate dateOfBirth, String city, String state,
                             String educationLevel, String preferredCity, Instant createdAt, Instant updatedAt) {
}
