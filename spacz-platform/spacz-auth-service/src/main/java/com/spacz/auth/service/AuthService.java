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
}
