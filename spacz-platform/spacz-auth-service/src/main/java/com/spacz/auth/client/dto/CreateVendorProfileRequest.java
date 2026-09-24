package com.spacz.auth.client.dto;

public record CreateVendorProfileRequest(Long vendorId, String email, String businessName, String contactName,
                                         String phone, String city) {
}
