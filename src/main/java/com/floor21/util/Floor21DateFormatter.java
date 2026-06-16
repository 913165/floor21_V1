package com.floor21.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Floor21 display dates: {@code 28-Jun-2026} (dd-MMM-yyyy). */
public final class Floor21DateFormatter {

    public static final String DISPLAY_PATTERN = "dd-MMM-yyyy";

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern(DISPLAY_PATTERN, Locale.ENGLISH);

    private static final List<DateTimeFormatter> PARSE_FORMATS =
            List.of(
                    DISPLAY,
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"));

    private Floor21DateFormatter() {}

    public static String formatDisplay(LocalDate date) {
        return date != null ? DISPLAY.format(date) : "—";
    }

    public static LocalDate parseDisplay(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        for (DateTimeFormatter formatter : PARSE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        throw new IllegalArgumentException("Invalid date: " + trimmed + " (use dd-Mon-yyyy)");
    }
}
