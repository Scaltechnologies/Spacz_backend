package com.spacz.admin.security;

public enum Role {
    ADMIN,
    USER,
    VENDOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
