package com.spacz.studyhall.entity;

public enum StudyHallStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED,
    /** Listed in search and bookable. */
    ACTIVE,
    INACTIVE
}
