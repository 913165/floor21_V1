package com.floor21.dto;

import java.util.List;
import java.util.UUID;

public record ParkingPlanDto(
        int floorNumber,
        int slotCount,
        List<Integer> topRow,
        List<Integer> bottomRow,
        List<ParkingPlanSlotDto> slots) {

    public record ParkingPlanSlotDto(
            int slotNumber,
            UUID flatId,
            String flatNumber,
            UUID linkedResidentialFlatId,
            String linkedResidentialFlatNumber) {}
}
