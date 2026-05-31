package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Supported residential unit types for flat grid layout and admin edits. */
public final class ResidentialBhkTypes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> NAMED_TYPES = Set.of("STUDIO", "PENTHOUSE", "DUPLEX");

    private static final List<String> ALL =
            List.of(
                    "STUDIO",
                    "1BHK",
                    "1.5BHK",
                    "2BHK",
                    "2.5BHK",
                    "3BHK",
                    "3.5BHK",
                    "4BHK",
                    "4.5BHK",
                    "5BHK",
                    "5.5BHK",
                    "6BHK",
                    "6.5BHK",
                    "7BHK",
                    "PENTHOUSE",
                    "DUPLEX");

    private ResidentialBhkTypes() {}

    public static List<String> all() {
        return ALL;
    }

    /** Studio and 1–7 BHK only — used for uniform per-floor layout (building form, generate flats). */
    public static List<String> perFloorLayout() {
        return ALL.stream().filter(t -> !"PENTHOUSE".equals(t) && !"DUPLEX".equals(t)).toList();
    }

    public static Map<String, Integer> emptyCountMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String type : ALL) {
            map.put(type, 0);
        }
        return map;
    }

    public static Map<String, Integer> countsFromBuilding(Building building) {
        if (building == null) {
            return emptyCountMap();
        }
        if (building.getBhkMixPerFloor() != null && !building.getBhkMixPerFloor().isBlank()) {
            return mixFromJson(building.getBhkMixPerFloor());
        }
        Map<String, Integer> map = emptyCountMap();
        if (building.getBhk1PerFloor() != null) {
            map.put("1BHK", building.getBhk1PerFloor());
        }
        if (building.getBhk2PerFloor() != null) {
            map.put("2BHK", building.getBhk2PerFloor());
        }
        if (building.getBhk3PerFloor() != null) {
            map.put("3BHK", building.getBhk3PerFloor());
        }
        return map;
    }

    public static void persistMixOnBuilding(Building building, Map<String, Integer> mix) {
        Map<String, Integer> normalized = normalizeMix(mix);
        building.setBhkMixPerFloor(mixToJson(normalized));
        building.setBhk1PerFloor(normalized.get("1BHK"));
        building.setBhk2PerFloor(normalized.get("2BHK"));
        building.setBhk3PerFloor(normalized.get("3BHK"));
        building.setBhkPerFloor(normalized);
    }

    public static Map<String, Integer> normalizeMix(Map<String, Integer> mix) {
        Map<String, Integer> normalized = emptyCountMap();
        if (mix == null) {
            return normalized;
        }
        for (String type : ALL) {
            Integer count = mix.get(type);
            normalized.put(type, count != null ? Math.max(0, count) : 0);
        }
        return normalized;
    }

    public static String mixToJson(Map<String, Integer> mix) {
        try {
            return JSON.writeValueAsString(normalizeMix(mix));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize unit mix", e);
        }
    }

    public static Map<String, Integer> mixFromJson(String json) {
        if (json == null || json.isBlank()) {
            return emptyCountMap();
        }
        try {
            Map<String, Integer> raw = JSON.readValue(json, new TypeReference<>() {});
            return normalizeMix(raw);
        } catch (JsonProcessingException e) {
            return emptyCountMap();
        }
    }

    public static String normalize(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            throw new IllegalArgumentException("Unit type is required.");
        }
        String trimmed = unitType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("STUDIO".equals(trimmed) || "STD".equals(trimmed)) {
            return "STUDIO";
        }
        if ("PENTHOUSE".equals(trimmed) || "PH".equals(trimmed)) {
            return "PENTHOUSE";
        }
        if ("DUPLEX".equals(trimmed)) {
            return "DUPLEX";
        }
        if (!trimmed.endsWith("BHK")) {
            trimmed = trimmed + "BHK";
        }
        String normalized = normalizeNumericBhkForm(trimmed);
        if (!ALL.contains(normalized)) {
            throw new IllegalArgumentException("Unit type must be one of: " + String.join(", ", ALL));
        }
        return normalized;
    }

    /** Relative size used for defaults and floor-plan grouping (studio ≈ 0.5 BHK, penthouse ≈ 8 BHK). */
    public static double layoutSize(String unitType) {
        String normalized = normalize(unitType);
        if ("STUDIO".equals(normalized)) {
            return 0.5;
        }
        if ("PENTHOUSE".equals(normalized)) {
            return 8.0;
        }
        if ("DUPLEX".equals(normalized)) {
            return 4.0;
        }
        return numericBhkValue(normalized);
    }

    /** Maps unit type to uploaded floor-plan slot (1bhk / 2bhk / 3bhk), or null if none applies. */
    public static String resolveFloorPlanSlot(String unitType, Building building) {
        if (building == null || unitType == null || unitType.isBlank()) {
            return null;
        }
        String normalized = normalize(unitType);
        if ("STUDIO".equals(normalized) && building.getFloorPlan1Bhk() != null) {
            return "1bhk";
        }
        if ("PENTHOUSE".equals(normalized) && building.getFloorPlan3Bhk() != null) {
            return "3bhk";
        }
        double size = layoutSize(normalized);
        if (size <= 1.5 && building.getFloorPlan1Bhk() != null) {
            return "1bhk";
        }
        if (size <= 2.5 && building.getFloorPlan2Bhk() != null) {
            return "2bhk";
        }
        if (size <= 3.5 && building.getFloorPlan3Bhk() != null) {
            return "3bhk";
        }
        return null;
    }

    public static int defaultAreaSqft(String unitType) {
        String normalized = normalize(unitType);
        if ("STUDIO".equals(normalized)) {
            return 420;
        }
        if ("PENTHOUSE".equals(normalized)) {
            return 2800;
        }
        if ("DUPLEX".equals(normalized)) {
            return 1800;
        }
        return (int) Math.round(280 + layoutSize(normalized) * 270);
    }

    public static long defaultBasePrice(String unitType) {
        String normalized = normalize(unitType);
        if ("STUDIO".equals(normalized)) {
            return 3_200_000L;
        }
        if ("PENTHOUSE".equals(normalized)) {
            return 25_000_000L;
        }
        if ("DUPLEX".equals(normalized)) {
            return 12_000_000L;
        }
        return Math.round(layoutSize(normalized) * 4_200_000);
    }

    public static int sumCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return 0;
        }
        return counts.values().stream().mapToInt(v -> v != null ? Math.max(0, v) : 0).sum();
    }

    public static boolean isNamedType(String unitType) {
        return unitType != null && NAMED_TYPES.contains(unitType.trim().toUpperCase(Locale.ROOT));
    }

    private static String normalizeNumericBhkForm(String withBhkSuffix) {
        String numeric = withBhkSuffix.substring(0, withBhkSuffix.length() - 3);
        if (numeric.endsWith(".0")) {
            numeric = numeric.substring(0, numeric.length() - 2);
        }
        return numeric + "BHK";
    }

    private static double numericBhkValue(String normalizedBhk) {
        String numeric = normalizedBhk.substring(0, normalizedBhk.length() - 3);
        return Double.parseDouble(numeric);
    }
}
