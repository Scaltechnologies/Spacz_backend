package com.spacz.studyhall.service.support;

import java.util.Locale;
import java.util.Set;

/**
 * Seat numbers are unique per hall (case-insensitive).
 */
public final class SeatNumbering {

    private SeatNumbering() {
    }

    /** 1 → A, 26 → Z, 27 → AA. */
    public static String rowLabel(int row) {
        StringBuilder label = new StringBuilder();
        int n = row;
        while (n > 0) {
            n--;
            label.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return label.toString();
    }

    /** {@code wanted} if free, otherwise {@code wanted-2}, {@code wanted-3} ... (recorded in {@code taken}). */
    public static String unique(String wanted, Set<String> takenLowercase) {
        String candidate = wanted;
        int suffix = 2;
        while (takenLowercase.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = wanted + "-" + suffix++;
        }
        takenLowercase.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }
}
