package com.spacz.studyhall.client.dto;

public record UserSummaryDto(Long userId, String firstName, String lastName, String email, String phone, String city) {
}
