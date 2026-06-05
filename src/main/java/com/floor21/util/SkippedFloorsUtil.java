package com.floor21.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Parses and applies building floor-number skip lists (e.g. omit 13th floor). */
public final class SkippedFloorsUtil {

    private SkippedFloorsUtil() {}

    public static Set<Integer> parseSet(String input) {
        if (input == null || input.isBlank()) {
            return Set.of();
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed);
        }
        Set<Integer> floors = new TreeSet<>();
        for (String part : trimmed.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                int value = Integer.parseInt(part.trim());
                if (value > 0) {
                    floors.add(value);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid floor number in skip list: " + part.trim());
            }
        }
        return floors;
    }

    private static Set<Integer> parseJsonArray(String json) {
        Set<Integer> floors = new TreeSet<>();
        String inner = json.substring(1, json.lastIndexOf(']')).trim();
        if (inner.isBlank()) {
            return floors;
        }
        for (String part : inner.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(token);
                if (value > 0) {
                    floors.add(value);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid floor number in skip list: " + token);
            }
        }
        return floors;
    }

    public static String normalize(String input) {
        Set<Integer> floors = parseSet(input);
        if (floors.isEmpty()) {
            return null;
        }
        return floors.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public static String formatForDisplay(String stored) {
        String normalized = normalize(stored);
        if (normalized == null) {
            return "";
        }
        return normalized.replace(",", ", ");
    }

    public static void validateForBuilding(Set<Integer> skipped, int totalFloors, int parkingFloors) {
        if (skipped.isEmpty()) {
            return;
        }
        for (int floor : skipped) {
            if (floor < 1 || floor > totalFloors) {
                throw new IllegalArgumentException(
                        "Skipped floor "
                                + floor
                                + " is outside the building range (1–"
                                + totalFloors
                                + ").");
            }
        }
        if (parkingFloors < 0 || parkingFloors > totalFloors) {
            return;
        }
        int activeResidential = countActiveFloors(parkingFloors + 1, totalFloors, skipped);
        if (parkingFloors < totalFloors && activeResidential == 0) {
            throw new IllegalArgumentException(
                    "At least one residential floor must remain after applying the skip list.");
        }
    }

    public static int countActiveFloors(int fromInclusive, int toInclusive, Set<Integer> skipped) {
        int count = 0;
        for (int floor = fromInclusive; floor <= toInclusive; floor++) {
            if (!skipped.contains(floor)) {
                count++;
            }
        }
        return count;
    }

    public static List<Integer> activeFloors(int fromInclusive, int toInclusive, Set<Integer> skipped) {
        List<Integer> floors = new ArrayList<>();
        for (int floor = fromInclusive; floor <= toInclusive; floor++) {
            if (!skipped.contains(floor)) {
                floors.add(floor);
            }
        }
        return floors;
    }

    public static boolean setsEqual(String a, String b) {
        return parseSet(a).equals(parseSet(b));
    }
}
