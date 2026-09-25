package com.spacz.user.security;

public enum Role {
    ADMIN,
    USER,
    VENDOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
