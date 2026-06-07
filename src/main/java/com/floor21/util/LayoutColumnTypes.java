package com.floor21.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layout column position (1, 2, 3…) and optional per-column type label (A, B, custom). */
public final class LayoutColumnTypes {

    private LayoutColumnTypes() {}

    public static List<Integer> columnNumbersForFlatsPerFloor(int flatsPerFloor) {
        int count = Math.max(0, flatsPerFloor);
        List<Integer> numbers = new ArrayList<>(count);
        for (int column = 1; column <= count; column++) {
            numbers.add(column);
        }
        return numbers;
    }

    /** Optional display label configured manually per column (blank → none). */
    public static String normalizeTypeLabel(String typeLabel) {
        if (typeLabel == null || typeLabel.isBlank()) {
            return null;
        }
        return typeLabel.trim();
    }

    /**
     * Legacy: unit 1 → A, unit 2 → B. Used only when migrating old column-default keys.
     *
     * @deprecated column defaults are keyed by column number, not letter
     */
    @Deprecated
    public static String legacyLetterForUnitNumber(int unitNumber) {
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be at least 1.");
        }
        if (unitNumber <= 26) {
            return String.valueOf((char) ('A' + unitNumber - 1));
        }
        return String.valueOf(unitNumber);
    }

    public static String formatGridTypeLabel(String bhkType, String typeLabel) {
        if (bhkType == null || bhkType.isBlank()) {
            return "";
        }
        String normalizedLabel = normalizeTypeLabel(typeLabel);
        if (normalizedLabel == null) {
            return bhkType.trim();
        }
        return bhkType.trim() + " · " + normalizedLabel;
    }

    /** Normalizes stored default keys: numeric column numbers; legacy A→1, B→2, … */
    public static String normalizeColumnDefaultsKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.matches("\\d+")) {
            int n = Integer.parseInt(trimmed);
            return n >= 1 ? trimmed : null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.length() == 1) {
            char c = upper.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return String.valueOf(c - 'A' + 1);
            }
        }
        return trimmed;
    }

    public static void validateColumnNumber(int columnNumber) {
        if (columnNumber < 1) {
            throw new IllegalArgumentException("Column number must be at least 1.");
        }
    }

    public static String columnDefaultsKey(int columnNumber) {
        validateColumnNumber(columnNumber);
        return String.valueOf(columnNumber);
    }
}
