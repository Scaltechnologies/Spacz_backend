package com.spacz.user.security;

import com.spacz.user.exception.ForbiddenException;

/**
 * Ownership rules for /api/users/{userId}/**: the path userId must be the caller's own ID.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    public static void requireSelf(AuthenticatedUser caller, Long userId) {
        if (!caller.userId().equals(userId)) {
            throw new ForbiddenException("You can only modify your own profile");
        }
    }

    public static void requireSelfOrAdmin(AuthenticatedUser caller, Long userId) {
        if (!caller.isAdmin() && !caller.userId().equals(userId)) {
            throw new ForbiddenException("You can only access your own profile");
        }
    }
}
