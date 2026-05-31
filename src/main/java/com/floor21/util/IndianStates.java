package com.floor21.util;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Indian states and union territories for address forms. */
public final class IndianStates {

    private static final Pattern PIN_PATTERN = Pattern.compile("^[1-9][0-9]{5}$");

    private static final List<String> ALL = List.of(
            "Andaman and Nicobar Islands",
            "Andhra Pradesh",
            "Arunachal Pradesh",
            "Assam",
            "Bihar",
            "Chandigarh",
            "Chhattisgarh",
            "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi",
            "Goa",
            "Gujarat",
            "Haryana",
            "Himachal Pradesh",
            "Jammu and Kashmir",
            "Jharkhand",
            "Karnataka",
            "Kerala",
            "Ladakh",
            "Lakshadweep",
            "Madhya Pradesh",
            "Maharashtra",
            "Manipur",
            "Meghalaya",
            "Mizoram",
            "Nagaland",
            "Odisha",
            "Puducherry",
            "Punjab",
            "Rajasthan",
            "Sikkim",
            "Tamil Nadu",
            "Telangana",
            "Tripura",
            "Uttar Pradesh",
            "Uttarakhand",
            "West Bengal");

    private static final Set<String> KNOWN = Set.copyOf(ALL);

    private IndianStates() {}

    public static List<String> all() {
        return ALL;
    }

    public static boolean isKnownState(String state) {
        return state != null && KNOWN.contains(state);
    }

    public static boolean isValidPin(String pin) {
        return pin != null && PIN_PATTERN.matcher(pin).matches();
    }

    public static String normalizePin(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static String normalizeState(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
