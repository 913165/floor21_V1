package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-floor parking slot configuration stored as JSON on {@link Building}. */
public final class ParkingFloorConfigUtil {

    public static final int DEFAULT_GRID_COLS = 14;
    public static final int DEFAULT_GRID_ROWS = 8;
    public static final int DEFAULT_CAR_SIZE_PERCENT = 100;

    private static final ObjectMapper JSON = new ObjectMapper();

    public record GridPlacement(int slotNumber, int col, int row, String orientation) {}

    public record FloorConfig(
            int slotCount,
            boolean configured,
            Integer gridCols,
            Integer gridRows,
            List<GridPlacement> placements,
            Integer carSizePercent) {

        public FloorConfig(int slotCount, boolean configured) {
            this(slotCount, configured, null, null, null, null);
        }
    }

    private ParkingFloorConfigUtil() {}

    public static int normalizeCarSizePercent(Integer value) {
        if (value == null) {
            return DEFAULT_CAR_SIZE_PERCENT;
        }
        return Math.max(50, Math.min(150, value));
    }

    public static int resolveCarSizePercent(FloorConfig config) {
        if (config == null) {
            return DEFAULT_CAR_SIZE_PERCENT;
        }
        return normalizeCarSizePercent(config.carSizePercent());
    }

    public static Map<Integer, FloorConfig> read(Building building) {
        if (building == null || building.getParkingFloorConfig() == null || building.getParkingFloorConfig().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, FloorConfig> raw =
                    JSON.readValue(building.getParkingFloorConfig(), new TypeReference<>() {});
            Map<Integer, FloorConfig> out = new LinkedHashMap<>();
            for (Map.Entry<String, FloorConfig> e : raw.entrySet()) {
                out.put(Integer.parseInt(e.getKey()), e.getValue());
            }
            return out;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static FloorConfig forFloor(Building building, int floorNumber) {
        return read(building).getOrDefault(floorNumber, new FloorConfig(0, false));
    }

    public static boolean isConfigured(Building building, int floorNumber) {
        return forFloor(building, floorNumber).configured();
    }

    public static List<GridPlacement> defaultGridPlacements(int slotCount, int gridCols, int gridRows) {
        int bottomCount = (int) Math.ceil(slotCount / 2.0);
        int topCount = slotCount - bottomCount;
        int topRowIdx = 0;
        int bottomRowIdx = Math.min(gridRows - 1, 6);
        if (bottomRowIdx <= topRowIdx + 2) {
            bottomRowIdx = Math.min(gridRows - 1, topRowIdx + 3);
        }
        List<GridPlacement> out = new ArrayList<>();
        for (int slot = 1; slot <= bottomCount; slot++) {
            out.add(new GridPlacement(slot, Math.min(slot - 1, gridCols - 1), bottomRowIdx, "vertical"));
        }
        for (int i = 0; i < topCount; i++) {
            int slotNum = bottomCount + topCount - i;
            out.add(new GridPlacement(slotNum, Math.min(i, gridCols - 1), topRowIdx, "vertical"));
        }
        return out;
    }

    public static void markConfigured(
            Building building, int floorNumber, int slotCount, int carSizePercent) {
        Map<Integer, FloorConfig> map = new LinkedHashMap<>(read(building));
        FloorConfig existing = map.get(floorNumber);
        int gridCols =
                existing != null && existing.gridCols() != null
                        ? existing.gridCols()
                        : DEFAULT_GRID_COLS;
        int gridRows =
                existing != null && existing.gridRows() != null
                        ? existing.gridRows()
                        : DEFAULT_GRID_ROWS;
        List<GridPlacement> placements;
        if (existing != null
                && existing.configured()
                && existing.slotCount() == slotCount
                && existing.placements() != null
                && !existing.placements().isEmpty()) {
            placements = existing.placements();
        } else {
            placements = defaultGridPlacements(slotCount, gridCols, gridRows);
        }
        map.put(
                floorNumber,
                new FloorConfig(
                        slotCount,
                        true,
                        gridCols,
                        gridRows,
                        placements,
                        normalizeCarSizePercent(carSizePercent)));
        building.setParkingFloorConfig(toJson(map));
    }

    public static void saveLayout(
            Building building,
            int floorNumber,
            int gridCols,
            int gridRows,
            List<GridPlacement> placements) {
        Map<Integer, FloorConfig> map = new LinkedHashMap<>(read(building));
        FloorConfig existing = map.getOrDefault(floorNumber, new FloorConfig(0, false));
        map.put(
                floorNumber,
                new FloorConfig(
                        existing.slotCount(),
                        true,
                        gridCols,
                        gridRows,
                        List.copyOf(placements),
                        resolveCarSizePercent(existing)));
        building.setParkingFloorConfig(toJson(map));
    }

    public static void clearAll(Building building) {
        building.setParkingFloorConfig(null);
    }

    private static String toJson(Map<Integer, FloorConfig> map) {
        try {
            Map<String, FloorConfig> keyed = new LinkedHashMap<>();
            for (Map.Entry<Integer, FloorConfig> e : map.entrySet()) {
                keyed.put(String.valueOf(e.getKey()), e.getValue());
            }
            return JSON.writeValueAsString(keyed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize parking floor config", ex);
        }
    }
}
