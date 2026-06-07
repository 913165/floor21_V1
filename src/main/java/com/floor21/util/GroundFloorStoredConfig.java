package com.floor21.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.floor21.util.ParkingFloorConfigUtil.FloorConfig;
import com.floor21.util.ParkingFloorConfigUtil.GridPlacement;
import java.math.BigDecimal;
import java.util.List;

/** JSON root for {@code buildings.ground_floor_config} (supports legacy bare {@link FloorConfig}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroundFloorStoredConfig(FloorConfig shops, GroundFloorParkingConfig parking) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GroundFloorParkingConfig(
            int slotCount,
            BigDecimal slotAreaSqft,
            Integer carSizePercent,
            List<GridPlacement> placements) {}

    public static GroundFloorStoredConfig empty() {
        return new GroundFloorStoredConfig(new FloorConfig(0, false), null);
    }

    public int parkingSlotCount() {
        return parking != null ? parking.slotCount() : 0;
    }
}
