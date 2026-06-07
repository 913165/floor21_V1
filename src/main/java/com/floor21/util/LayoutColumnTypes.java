package com.floor21.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Vertical column labels (A, B, C…) — same unit position on every residential floor. */
public final class LayoutColumnTypes {

    private LayoutColumnTypes() {}

    /** Unit 1 → A, unit 2 → B, … unit 26 → Z; above 26 uses the numeric unit. */
    public static String columnTypeForUnitNumber(int unitNumber) {
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be at least 1.");
        }
        if (unitNumber <= 26) {
            return String.valueOf((char) ('A' + unitNumber - 1));
        }
        return String.valueOf(unitNumber);
    }

    public static String normalize(String columnType) {
        if (columnType == null || columnType.isBlank()) {
            return null;
        }
        return columnType.trim().toUpperCase(Locale.ROOT);
    }

    public static List<String> typesForFlatsPerFloor(int flatsPerFloor) {
        int count = Math.max(0, flatsPerFloor);
        List<String> types = new ArrayList<>(count);
        for (int unit = 1; unit <= count; unit++) {
            types.add(columnTypeForUnitNumber(unit));
        }
        return types;
    }

    public static String formatGridTypeLabel(String bhkType, String columnType) {
        if (bhkType == null || bhkType.isBlank()) {
            return "";
        }
        String normalizedColumn = normalize(columnType);
        if (normalizedColumn == null) {
            return bhkType.trim();
        }
        return bhkType.trim() + " · " + normalizedColumn;
    }
}
