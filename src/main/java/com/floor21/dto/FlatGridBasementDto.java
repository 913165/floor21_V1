package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FlatGridBasementDto(
        int floorNumber,
        String label,
        boolean configured,
        int slotCount,
        String rangeLabel,
        int parkingCarSizePercent,
        int gridRows,
        int minGridRows,
        int carLiftCount,
        int passengerLiftCount,
        int gateCount,
        BigDecimal slotAreaSqft,
        boolean hasLayoutImage,
        UUID firstFlatId,
        BigDecimal areaSqft,
        BigDecimal basePrice) {}

