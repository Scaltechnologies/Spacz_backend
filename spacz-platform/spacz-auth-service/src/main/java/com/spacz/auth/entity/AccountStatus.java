package com.spacz.auth.entity;

public enum AccountStatus {
    /** Can log in. */
    ACTIVE,
    /** Blocked by an administrator; can be re-activated. */
    SUSPENDED,
    /** Permanently closed. */
    DISABLED
}
