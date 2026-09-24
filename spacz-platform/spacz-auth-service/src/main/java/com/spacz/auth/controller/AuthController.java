package com.spacz.auth.controller;

import com.spacz.auth.config.OpenApiConfig;
import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.ChangePasswordRequest;
import com.spacz.auth.dto.LoginRequest;
import com.spacz.auth.dto.OtpRequest;
import com.spacz.auth.dto.OtpRequestResponse;
import com.spacz.auth.dto.OtpVerifyRequest;
import com.spacz.auth.dto.RefreshTokenRequest;
import com.spacz.auth.dto.RegisterRequest;
import com.spacz.auth.dto.RegisterUserRequest;
import com.spacz.auth.dto.RegisterVendorRequest;
import com.spacz.auth.exception.ApiError;
import com.spacz.auth.security.AuthenticatedUser;
import com.spacz.auth.service.AuthService;
import com.spacz.auth.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Email/password and phone-OTP registration and login, token refresh, logout")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a student (role USER) or vendor (role VENDOR) with email + password")
    @ApiResponse(responseCode = "201", description = "Registered and logged in")
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email or phone already registered", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Profile service unavailable; nothing was created", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/register/user")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a student (shortcut for role USER)")
    public AuthResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/register/vendor")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a vendor (shortcut for role VENDOR)",
            description = "The vendor profile starts as DRAFT; the vendor completes it and submits it for approval.")
    public AuthResponse registerVendor(@Valid @RequestBody RegisterVendorRequest request) {
        return authService.registerVendor(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email (or phone) and password")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "ACCOUNT_SUSPENDED, ACCOUNT_DISABLED or ACCOUNT_LOCKED", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a one-time code to a phone number",
            description = "Works for existing and new numbers alike (does not reveal whether an account exists).")
    @ApiResponse(responseCode = "429", description = "OTP_TOO_SOON or OTP_RATE_LIMITED", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        return otpService.requestCode(request.phone(), http.getRemoteAddr());
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify the code: log in, or register a new phone number")
    @ApiResponse(responseCode = "401", description = "Code invalid or expired", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "REGISTRATION_REQUIRED: resend with role (and firstName for USER)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return otpService.verify(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "Refresh tokens are single use. Reusing an already-rotated token revokes all sessions of the account.")
    @ApiResponse(responseCode = "401", description = "Invalid, expired or reused refresh token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token", description = "Always returns 204. The access token expires on its own.")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "The authenticated account", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public AccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.getAccount(user.userId());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set or change the password (signs out other sessions)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.userId(), request);
    }
}
