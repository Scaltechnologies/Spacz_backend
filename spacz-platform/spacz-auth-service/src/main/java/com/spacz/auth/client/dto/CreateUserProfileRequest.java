package com.spacz.auth.client.dto;

public record CreateUserProfileRequest(Long userId, String email, String firstName, String lastName, String phone,
                                       String city) {
}
