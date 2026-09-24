package com.spacz.admin.entity;

/**
 * Everything recorded in the platform audit log. Admin actions are recorded by admin-service
 * itself; domain events are reported by auth-service and studyhall-service.
 */
public enum AuditAction {
    // admin actions
    USER_SUSPENDED,
    USER_ACTIVATED,
    VENDOR_APPROVED,
    VENDOR_REJECTED,
    VENDOR_SUSPENDED,
    VENDOR_ACTIVATED,
    STUDY_HALL_APPROVED,
    STUDY_HALL_REJECTED,
    STUDY_HALL_SUSPENDED,
    STUDY_HALL_ACTIVATED,
    PROGRAM_CREATED,
    PROGRAM_UPDATED,
    PROGRAM_DEACTIVATED,
    AMENITY_CREATED,
    AMENITY_UPDATED,
    // domain events from other services
    USER_REGISTERED,
    VENDOR_REGISTERED,
    VENDOR_SUBMITTED,
    STUDY_HALL_CREATED,
    STUDY_HALL_UPDATED,
    STUDY_HALL_SUBMITTED,
    STUDY_HALL_DELETED
}
