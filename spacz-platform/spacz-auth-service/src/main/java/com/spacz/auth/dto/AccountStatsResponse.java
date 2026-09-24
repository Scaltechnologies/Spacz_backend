package com.spacz.auth.dto;

public record AccountStatsResponse(long totalUsers, long activeUsers, long suspendedUsers,
                                   long totalVendorAccounts, long activeVendorAccounts, long totalAdmins) {
}
