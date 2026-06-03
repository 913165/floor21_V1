package com.floor21.dto;

import java.util.List;

public record FlatGridFloorDto(
        int floorNumber,
        String label,
        List<FlatGridFlatDto> flats,
        boolean parkingSection,
        int parkingSlotCount,
        String parkingRangeLabel,
        boolean parkingConfigured) {

    /** Residential or mixed floor row. */
    public FlatGridFloorDto(int floorNumber, String label, List<FlatGridFlatDto> flats) {
        this(floorNumber, label, flats, false, 0, null, false);
    }
}
