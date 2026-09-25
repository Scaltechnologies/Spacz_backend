package com.spacz.studyhall.dto.booking;

import com.spacz.studyhall.dto.enrollment.StudentInfo;

/**
 * A booking with the student's contact details ({@code student} is null when user-service is unavailable).
 */
public record VendorBookingResponse(BookingResponse booking, StudentInfo student) {
}
