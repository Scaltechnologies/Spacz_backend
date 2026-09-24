package com.spacz.auth.service.support;

import com.spacz.auth.exception.SpaczException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Normalises phone numbers to E.164 (+CCNNNN...), so "98765 43210", "09876543210" and
 * "+91-9876543210" are the same login identifier.
 */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[\\s\\-().]", "");
        String normalized;
        if (digits.startsWith("+")) {
            normalized = digits;
        } else if (digits.startsWith("00")) {
            normalized = "+" + digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 11) {
            normalized = defaultCountryCode + digits.substring(1);
        } else if (digits.length() == 10) {
            normalized = defaultCountryCode + digits;
        } else if (digits.length() > 10 && digits.startsWith(defaultCountryCode.substring(1))) {
            normalized = "+" + digits;
        } else {
            normalized = "+" + digits;
        }
        if (!E164.matcher(normalized).matches()) {
            throw new SpaczException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Invalid phone number: " + raw);
        }
        return normalized;
    }

    /** +919876543210 → +91******3210 (for responses and logs). */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }
}
