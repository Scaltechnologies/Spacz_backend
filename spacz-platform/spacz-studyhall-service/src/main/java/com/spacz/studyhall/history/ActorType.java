package com.spacz.studyhall.history;

/** Who caused a recorded change. */
public enum ActorType {
    USER,
    VENDOR,
    ADMIN,
    /** Scheduled jobs, payment reconciliation, webhooks and imports. */
    SYSTEM
}
