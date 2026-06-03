package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.entity.Building;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-floor parking slot configuration stored as JSON on {@link Building}. */
public final class ParkingFloorConfigUtil {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record FloorConfig(int slotCount, boolean configured) {}

    private ParkingFloorConfigUtil() {}

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

    public static void markConfigured(Building building, int floorNumber, int slotCount) {
        Map<Integer, FloorConfig> map = new LinkedHashMap<>(read(building));
        map.put(floorNumber, new FloorConfig(slotCount, true));
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
