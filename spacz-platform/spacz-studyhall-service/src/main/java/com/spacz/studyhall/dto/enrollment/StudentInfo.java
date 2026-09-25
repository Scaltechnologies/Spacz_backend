package com.spacz.studyhall.dto.enrollment;

/**
 * @param account true = SPACZ account (details from user-service); false = walk-in guest added by the vendor
 */
public record StudentInfo(Long userId, String name, String phone, String email, boolean account) {
}
