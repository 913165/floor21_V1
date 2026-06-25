package com.floor21.dto;

import java.math.BigDecimal;
import java.util.List;

public record FlatGridFloorDto(
        int floorNumber,
        String label,
        List<FlatGridFlatDto> flats,
        boolean parkingSection,
        int parkingSlotCount,
        String parkingRangeLabel,
        boolean parkingConfigured,
        int parkingCarSizePercent,
        int parkingGridRows,
        int parkingMinGridRows,
        int parkingCarLiftCount,
        int parkingPassengerLiftCount,
        int parkingGateCount,
        BigDecimal parkingSlotAreaSqft,
        boolean parkingHasLayoutImage,
        boolean floorHasActiveBooking,
        boolean convertibleFloorUse) {

    /** Residential or mixed floor row. */
    public FlatGridFloorDto(int floorNumber, String label, List<FlatGridFlatDto> flats) {
        this(floorNumber, label, flats, false, 0, null, false, 100, 1, 1, 0, 0, 1, null, false, false, false);
    }
}
