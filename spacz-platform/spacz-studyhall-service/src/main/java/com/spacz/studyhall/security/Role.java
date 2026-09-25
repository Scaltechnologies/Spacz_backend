package com.spacz.studyhall.security;

public enum Role {
    ADMIN,
    USER,
    VENDOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
