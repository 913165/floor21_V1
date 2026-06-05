package com.floor21.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ParkingFloorConfigDto(
        @NotNull @Min(1) @Max(200) Integer slotCount,
        @Min(50) @Max(200) Integer carSizePercent,
        @Min(0) @Max(8) Integer carLiftCount,
        @Min(0) @Max(8) Integer passengerLiftCount,
        @Min(0) @Max(8) Integer liftCount,
        @Min(0) @Max(8) Integer gateCount,
        Boolean showLift,
        Boolean showGate,
        @DecimalMin(value = "0.01", message = "Parking slot area must be greater than zero.")
                BigDecimal slotAreaSqft) {}
