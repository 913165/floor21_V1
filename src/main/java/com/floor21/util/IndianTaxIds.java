package com.floor21.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Format checks for common Indian tax and contact identifiers. */
public final class IndianTaxIds {

    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern TAN = Pattern.compile("^[A-Z]{4}[0-9]{5}[A-Z]$");
    private static final Pattern GSTIN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private IndianTaxIds() {}

    public static String normalizePan(String raw) {
        return normalizeUpper(raw);
    }

    public static String normalizeTan(String raw) {
        return normalizeUpper(raw);
    }

    public static String normalizeGstin(String raw) {
        return normalizeUpper(raw);
    }

    public static String normalizeMobile(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValidPan(String pan) {
        return pan != null && PAN.matcher(pan).matches();
    }

    public static boolean isValidTan(String tan) {
        return tan != null && TAN.matcher(tan).matches();
    }

    public static boolean isValidGstin(String gstin) {
        return gstin != null && GSTIN.matcher(gstin).matches();
    }

    public static boolean isValidMobile(String mobile) {
        return mobile != null && MOBILE.matcher(mobile).matches();
    }

    private static String normalizeUpper(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
