package com.spacz.admin.security;

/**
 * The caller, taken from the verified JWT. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long userId, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isVendor() {
        return role == Role.VENDOR;
    }

    public boolean isUser() {
        return role == Role.USER;
    }
}
