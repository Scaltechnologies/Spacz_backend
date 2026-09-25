package com.spacz.admin.service;

import com.spacz.admin.dto.AdminProfileRequest;
import com.spacz.admin.dto.AdminProfileResponse;
import com.spacz.admin.security.AuthenticatedUser;

public interface AdminProfileService {

    /** The caller's admin profile, created on first access. */
    AdminProfileResponse me(AuthenticatedUser admin);

    AdminProfileResponse update(AuthenticatedUser admin, AdminProfileRequest request);
}
