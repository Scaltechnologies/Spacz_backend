package com.spacz.user.service;

/**
 * Removes a student's personal data when they delete their account (called by auth-service).
 */
public interface AccountErasureService {

    /**
     * Idempotent. Deletes the student's identity documents (rows and stored files) and exam choices, and
     * replaces the profile's personal fields with placeholders. The profile row itself is kept so that
     * bookings, payments and activity that reference the userId stay consistent for accounting.
     */
    void erase(Long userId);
}
