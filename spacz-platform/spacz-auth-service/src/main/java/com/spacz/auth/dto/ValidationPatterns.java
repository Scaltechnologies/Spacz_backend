package com.spacz.auth.dto;

public final class ValidationPatterns {

    /** 8-72 characters (BCrypt limit), at least one letter and one digit. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters and contain at least one letter and one digit";
    public static final String PHONE = "^\\+?[0-9][0-9 -]{6,18}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number";

    private ValidationPatterns() {
    }
}
