package com.floor21.dto;

import java.util.List;

public record ParkingPlanDto(
        int floorNumber,
        int slotCount,
        List<Integer> topRow,
        List<Integer> bottomRow,
        List<ParkingPlanSlotDto> slots) {

    public record ParkingPlanSlotDto(int slotNumber, String flatNumber) {}
}
