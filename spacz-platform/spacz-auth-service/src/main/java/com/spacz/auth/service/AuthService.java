package com.spacz.auth.service;

import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.ChangePasswordRequest;
import com.spacz.auth.dto.LoginRequest;
import com.spacz.auth.dto.RegisterRequest;
import com.spacz.auth.dto.RegisterUserRequest;
import com.spacz.auth.dto.RegisterVendorRequest;

public interface AuthService {

    /** Single entry point: dispatches on {@code role}. */
    AuthResponse register(RegisterRequest request);

    AuthResponse registerUser(RegisterUserRequest request);

    AuthResponse registerVendor(RegisterVendorRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);

    AccountResponse getAccount(Long accountId);

    void changePassword(Long accountId, ChangePasswordRequest request);

    /**
     * A student closes their own account: personal data is erased in user-service, the account is DISABLED,
     * its email and phone are released (the number can register again) and every session is revoked.
     * Bookings and payments stay as anonymous financial records. Students only (403 for vendors and admins).
     */
    void deleteOwnAccount(Long accountId);
}
