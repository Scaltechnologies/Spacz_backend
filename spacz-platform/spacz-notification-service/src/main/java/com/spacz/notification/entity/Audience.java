package com.spacz.notification.entity;

import com.spacz.notification.security.Role;

import java.util.List;

/**
 * Who a notification is for. For a personal notification this is the recipient's role; for a
 * broadcast it is every account with that role ({@link #ALL} = every account).
 */
public enum Audience {
    USER,
    VENDOR,
    ADMIN,
    ALL;

    /** The audiences whose broadcasts an account with this role sees. */
    public static List<Audience> visibleTo(Role role) {
        return List.of(Audience.valueOf(role.name()), ALL);
    }

    /** Roles reached by a broadcast to this audience. */
    public List<Role> roles() {
        return this == ALL ? List.of(Role.values()) : List.of(Role.valueOf(name()));
    }
}
