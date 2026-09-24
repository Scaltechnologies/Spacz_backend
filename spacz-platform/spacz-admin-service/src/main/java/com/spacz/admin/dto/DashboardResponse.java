package com.spacz.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Platform statistics. A group is null when its owning service did not answer; that service is
 * then listed in {@code unavailableServices}.
 */
public record DashboardResponse(UserStats users, VendorStats vendors, StudyHallStats studyHalls,
                                BookingStats bookings, List<String> unavailableServices, Instant generatedAt) {

    public record UserStats(long totalUsers, long activeUsers, long suspendedUsers, long totalVendorAccounts,
                            long totalAdmins) {
    }

    /** {@code approvedVendors} counts APPROVED and ACTIVE vendors; {@code draftVendors} have not submitted yet. */
    public record VendorStats(long totalVendors, long draftVendors, long pendingVendors, long approvedVendors,
                              long suspendedVendors, long rejectedVendors) {
    }

    public record StudyHallStats(long totalStudyHalls, long activeStudyHalls, long pendingApproval,
                                 long suspendedStudyHalls, long totalSeats, long activePrograms) {
    }

    /**
     * @param todaysBookings     bookings created today
     * @param occupiedSeatsToday confirmed bookings covering today
     * @param activeEnrollments  students currently enrolled in a hall
     */
    public record BookingStats(long totalBookings, long todaysBookings, long occupiedSeatsToday, long pendingBookings,
                               long confirmedBookings, long activeEnrollments) {
    }
}
