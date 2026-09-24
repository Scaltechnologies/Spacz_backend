package com.spacz.auth.dto.internal;

import com.spacz.auth.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A legacy identity (user_login row, owner or aspirant) to create as a password-less account;
 * the person logs in with phone OTP afterwards.
 */
public record ImportAccountRequest(Long legacyLoginId, @Size(max = 20) String phone, @Size(max = 254) String email,
                                   @NotNull Role role) {
}
