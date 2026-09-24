package com.spacz.studyhall.dto;

public final class ValidationPatterns {

    public static final String PHONE = "^\\+?[0-9][0-9 -]{6,18}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number";
    public static final String PINCODE = "^[0-9A-Za-z -]{3,10}$";
    public static final String PINCODE_MESSAGE = "must be a valid postal code";

    private ValidationPatterns() {
    }
}
