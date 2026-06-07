package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON map of layout column type (A, B, C…) → default areas and price on {@link Building}. */
public final class BuildingColumnTypeDefaultsUtil {

    private static final ObjectMapper JSON = new ObjectMapper();

    private BuildingColumnTypeDefaultsUtil() {}

    public static Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> read(Building building) {
        if (building == null
                || building.getColumnTypeDefaults() == null
                || building.getColumnTypeDefaults().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> raw =
                    JSON.readValue(
                            building.getColumnTypeDefaults(),
                            new TypeReference<
                                    LinkedHashMap<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry>>() {});
            Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                normalized.put(LayoutColumnTypes.normalize(entry.getKey()), entry.getValue());
            }
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse building column type defaults", ex);
        }
    }

    public static void putForColumn(
            Building building, String columnType, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry entry) {
        String normalized = LayoutColumnTypes.normalize(columnType);
        if (normalized == null) {
            throw new IllegalArgumentException("Column type is required.");
        }
        Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> map = new LinkedHashMap<>(read(building));
        map.put(normalized, entry);
        building.setColumnTypeDefaults(toJson(map));
    }

    private static String toJson(Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize building column type defaults", ex);
        }
    }
}
