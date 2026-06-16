package com.floor21.util;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses milestone schedule save POST bodies without Spring {@code @ModelAttribute} binding. */
public final class MilestoneScheduleSaveFormParser {

    private static final Pattern LINE_PARAM =
            Pattern.compile("^lines\\[(\\d+)]\\.(id|dueDate|milestoneLabel|percent|agreedAmount|extraAmount)$");

    private MilestoneScheduleSaveFormParser() {}

    public static BookingPaymentSlabBatchForm parse(HttpServletRequest request) {
        BookingPaymentSlabBatchForm form = new BookingPaymentSlabBatchForm();
        String bookingId = firstNonBlank(request, "bookingId");
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking is required");
        }
        form.setBookingId(UUID.fromString(bookingId.trim()));

        Map<Integer, BookingPaymentSlabBatchForm.Line> byIndex = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Matcher matcher = LINE_PARAM.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            String field = matcher.group(2);
            String value = lastNonBlank(entry.getValue());
            BookingPaymentSlabBatchForm.Line line =
                    byIndex.computeIfAbsent(index, ignored -> new BookingPaymentSlabBatchForm.Line());
            applyField(line, field, value);
        }

        if (byIndex.isEmpty()) {
            throw new IllegalArgumentException("No slab rows to save. Reload the booking and try again.");
        }
        form.setLines(new ArrayList<>(byIndex.values()));
        return form;
    }

    private static void applyField(BookingPaymentSlabBatchForm.Line line, String field, String value) {
        if (value == null) {
            return;
        }
        switch (field) {
            case "id" -> line.setId(UUID.fromString(value.trim()));
            case "dueDate" -> line.setDueDate(parseDueDate(value));
            case "milestoneLabel" -> line.setMilestoneLabel(value);
            case "percent" -> line.setPercent(new BigDecimal(value.replace(",", "").trim()));
            case "agreedAmount" -> line.setAgreedAmount(new BigDecimal(value.replace(",", "").trim()));
            case "extraAmount" -> line.setExtraAmount(new BigDecimal(value.replace(",", "").trim()));
            default -> { /* unknown field */ }
        }
    }

    public static LocalDate parseDueDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Floor21DateFormatter.parseDisplay(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid slab date: " + text.trim());
        }
    }

    private static String firstNonBlank(HttpServletRequest request, String name) {
        return lastNonBlank(request.getParameterValues(name));
    }

    private static String lastNonBlank(String[] values) {
        if (values == null) {
            return null;
        }
        String last = null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                last = value;
            }
        }
        return last;
    }
}
