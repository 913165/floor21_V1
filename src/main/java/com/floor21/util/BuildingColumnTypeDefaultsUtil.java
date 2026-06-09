package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON map of layout column number (1, 2, 3…) → default unit type, areas and price on {@link Building}. */
public final class BuildingColumnTypeDefaultsUtil {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record ColumnDefaultsEntry(
            String bhkType,
            String layoutColumnType,
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {

        public BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry toTypeDefaultsEntry() {
            return new BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry(
                    areaSqft, carpetAreaSqft, balconyAreaSqft, basePrice);
        }
    }

    private BuildingColumnTypeDefaultsUtil() {}

    public static Map<String, ColumnDefaultsEntry> read(Building building) {
        if (building == null
                || building.getColumnTypeDefaults() == null
                || building.getColumnTypeDefaults().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, ColumnDefaultsEntry> raw =
                    JSON.readValue(
                            building.getColumnTypeDefaults(),
                            new TypeReference<LinkedHashMap<String, ColumnDefaultsEntry>>() {});
            Map<String, ColumnDefaultsEntry> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, ColumnDefaultsEntry> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                String columnKey = LayoutColumnTypes.normalizeColumnDefaultsKey(entry.getKey());
                if (columnKey == null) {
                    continue;
                }
                normalized.put(columnKey, entry.getValue());
            }
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse building column type defaults", ex);
        }
    }

    public static Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> readAsTypeDefaults(
            Building building) {
        Map<String, ColumnDefaultsEntry> configured = read(building);
        Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, ColumnDefaultsEntry> entry : configured.entrySet()) {
            out.put(entry.getKey(), entry.getValue().toTypeDefaultsEntry());
        }
        return out;
    }

    public static void putForColumnNumber(
            Building building, int columnNumber, ColumnDefaultsEntry entry) {
        LayoutColumnTypes.validateColumnNumber(columnNumber);
        String key = LayoutColumnTypes.columnDefaultsKey(columnNumber);
        Map<String, ColumnDefaultsEntry> map = new LinkedHashMap<>(read(building));
        map.put(key, entry);
        building.setColumnTypeDefaults(toJson(map));
    }

    private static String toJson(Map<String, ColumnDefaultsEntry> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize building column type defaults", ex);
        }
    }
}
