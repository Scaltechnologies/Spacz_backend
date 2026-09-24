package com.spacz.studyhall.dto;

public record StatsResponse(long totalVendors, long draftVendors, long pendingVendors, long approvedVendors,
                            long activeVendors, long suspendedVendors, long rejectedVendors, long totalStudyHalls,
                            long activeStudyHalls, long pendingStudyHalls, long suspendedStudyHalls,
                            long totalSeats, long totalBookings, long todaysBookings, long occupiedSeatsToday,
                            long pendingBookings, long confirmedBookings, long activeEnrollments,
                            long activePrograms) {
}
