package com.spacz.studyhall.entity;

public enum BookingPlan {
    /** Priced per day, inclusive date range. */
    DAILY,
    /** Priced per calendar month; the end date is derived from the start date and the number of months. */
    MONTHLY
}
