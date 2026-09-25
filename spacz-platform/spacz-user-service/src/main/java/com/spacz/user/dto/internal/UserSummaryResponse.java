package com.spacz.user.dto.internal;

/**
 * Minimal student details other services may show (e.g. a vendor's booking list).
 */
public record UserSummaryResponse(Long userId, String firstName, String lastName, String email, String phone,
                                  String city) {
}
