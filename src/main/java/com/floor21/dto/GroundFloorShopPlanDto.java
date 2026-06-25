package com.floor21.dto;



import java.math.BigDecimal;

import java.util.List;

import java.util.UUID;



public record GroundFloorShopPlanDto(

        int shopCount,

        int parkingSlotCount,

        int gridCols,

        int gridRows,

        int minGridRows,

        List<ParkingGridPlacementDto> shopPlacements,

        List<ParkingGridPlacementDto> parkingPlacements,

        List<ShopPlanSlotDto> shops,

        List<GroundFloorParkingSlotDto> parkingSlots,

        List<ParkingFixturePlacementDto> fixtures,

        int carLiftCount,

        int passengerLiftCount,

        int gateCount,

        Integer parkingCarSizePercent,
        Integer shopSizePercent,
        boolean hasLayoutImage) {



    public record ShopPlanSlotDto(

            int slotNumber,

            UUID flatId,

            String flatNumber,

            BigDecimal areaSqft,

            BigDecimal basePrice,

            String status,

            boolean bookableByCurrentUser,

            UUID clientId,

            BigDecimal paymentReceived,

            BigDecimal remainingBalance,

            UUID assignedPartnerId,

            String assignedPartnerName) {}



    public record GroundFloorParkingSlotDto(
            int slotNumber,
            UUID flatId,
            String flatNumber,
            BigDecimal areaSqft,
            UUID linkedResidentialFlatId,
            String linkedResidentialFlatNumber) {}
}

