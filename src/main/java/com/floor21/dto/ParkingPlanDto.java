package com.floor21.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ParkingPlanDto(
        int floorNumber,
        int slotCount,
        List<Integer> topRow,
        List<Integer> bottomRow,
        List<ParkingPlanSlotDto> slots,
        int gridCols,
        int gridRows,
        List<ParkingGridPlacementDto> placements,
        boolean gridLayout,
        int carSizePercent,
        int minGridRows,
        int carLiftCount,
        int passengerLiftCount,
        int liftCount,
        int gateCount,
        List<ParkingFixturePlacementDto> fixtures) {

    public record ParkingPlanSlotDto(
            int slotNumber,
            UUID flatId,
            String flatNumber,
            UUID linkedResidentialFlatId,
            String linkedResidentialFlatNumber,
            BigDecimal areaSqft,
            UUID assignedPartnerId,
            String assignedPartnerName,
            boolean linkableByCurrentUser) {}
}
