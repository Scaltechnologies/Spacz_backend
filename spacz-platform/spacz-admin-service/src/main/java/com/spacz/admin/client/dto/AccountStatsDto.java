package com.spacz.admin.client.dto;

public record AccountStatsDto(long totalUsers, long activeUsers, long suspendedUsers, long totalVendorAccounts,
                              long activeVendorAccounts, long totalAdmins) {
}
