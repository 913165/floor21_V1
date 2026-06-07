package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** JSON map of residential unit type → default areas and price on {@link Building}. */
public final class BuildingUnitTypeDefaultsUtil {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record TypeDefaultsEntry(
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {}

    private BuildingUnitTypeDefaultsUtil() {}

    public static Map<String, TypeDefaultsEntry> read(Building building) {
        if (building == null || building.getUnitTypeDefaults() == null || building.getUnitTypeDefaults().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, TypeDefaultsEntry> raw =
                    JSON.readValue(
                            building.getUnitTypeDefaults(),
                            new TypeReference<LinkedHashMap<String, TypeDefaultsEntry>>() {});
            Map<String, TypeDefaultsEntry> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, TypeDefaultsEntry> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                normalized.put(normalizeTypeKey(entry.getKey()), entry.getValue());
            }
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse building unit type defaults", ex);
        }
    }

    public static TypeDefaultsEntry getForType(Building building, String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return null;
        }
        return read(building).get(normalizeTypeKey(unitType));
    }

    public static void putForType(Building building, String unitType, TypeDefaultsEntry entry) {
        if (unitType == null || unitType.isBlank()) {
            throw new IllegalArgumentException("Unit type is required.");
        }
        Map<String, TypeDefaultsEntry> map = new LinkedHashMap<>(read(building));
        map.put(normalizeTypeKey(unitType), entry);
        building.setUnitTypeDefaults(toJson(map));
    }

    private static String toJson(Map<String, TypeDefaultsEntry> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize building unit type defaults", ex);
        }
    }

    private static String normalizeTypeKey(String unitType) {
        return FlatUnitTypes.normalize(unitType);
    }
}
