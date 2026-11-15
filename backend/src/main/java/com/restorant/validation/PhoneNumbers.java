package com.restorant.validation;

import java.util.regex.Pattern;

/**
 * Philippine mobile numbers. Every accepted spelling is stored in one canonical form: +639XXXXXXXXX.
 * Accepted input: the 10 digits after +63 (9XXXXXXXXX), the 11-digit local form (09XXXXXXXXX),
 * or the international form with or without the plus sign (+639XXXXXXXXX / 639XXXXXXXXX).
 */
public final class PhoneNumbers {
    public static final String MESSAGE = "Enter a Philippine mobile number: +63 followed by 10 digits starting with 9 (for example 917 123 4567)";
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern MOBILE = Pattern.compile("(?:\\+?63|0)?(9\\d{9})");
    private PhoneNumbers() {}

    /** Returns the canonical +639XXXXXXXXX form, or null when the value is not a Philippine mobile number. */
    public static String tryNormalize(String raw) {
        if (raw == null) return null;
        var match = MOBILE.matcher(SEPARATORS.matcher(raw.trim()).replaceAll(""));
        return match.matches() ? "+63" + match.group(1) : null;
    }
    public static String normalize(String raw) {
        String value = tryNormalize(raw);
        if (value == null) throw new IllegalArgumentException(MESSAGE);
        return value;
    }
    public static boolean isValid(String raw) { return tryNormalize(raw) != null; }
    /** The familiar 11-digit local form (09XXXXXXXXX); values that cannot be normalized are returned unchanged. */
    public static String local(String raw) {
        String value = tryNormalize(raw);
        return value == null ? raw : "0" + value.substring(3);
    }
}
