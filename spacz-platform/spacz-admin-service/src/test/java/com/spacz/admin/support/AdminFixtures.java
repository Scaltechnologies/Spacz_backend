package com.spacz.admin.support;

import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.VendorProfileDto;

import java.time.Instant;
import java.util.List;

public final class AdminFixtures {

    private AdminFixtures() {
    }

    public static AdminVendorDto vendor(long vendorId, String status) {
        return new AdminVendorDto(new VendorProfileDto(7L, vendorId, "Focus Hall", "Ravi", "v@x.in", "+91999",
                null, "Hyderabad", null, null, null, null, null, status, null, List.of(), null, null, null, null), 2, 1);
    }

    public static AccountDto account(long id, String role, String status) {
        return new AccountDto(id, "u" + id + "@example.com", "+9199" + id, role, status, true, null, Instant.now());
    }
}
