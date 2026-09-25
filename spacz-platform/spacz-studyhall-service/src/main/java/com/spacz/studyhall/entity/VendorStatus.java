package com.spacz.studyhall.entity;

public enum VendorStatus {
    /** Profile being completed; not yet submitted for approval. */
    DRAFT,
    /** Submitted, waiting for an admin. */
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED,
    ACTIVE,
    INACTIVE;

    /** A vendor whose study halls may be listed and booked. */
    public boolean isOperational() {
        return this == APPROVED || this == ACTIVE;
    }
}
