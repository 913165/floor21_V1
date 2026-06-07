package com.floor21.dto;



import jakarta.validation.constraints.DecimalMin;

import jakarta.validation.constraints.Max;

import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;



public record GroundFloorConfigDto(

        @NotNull @Min(0) @Max(50) Integer shopCount,

        @DecimalMin(value = "0.01", message = "Shop area must be greater than zero.")

                BigDecimal shopAreaSqft,

        @Min(0) @Max(8) Integer carLiftCount,

        @Min(0) @Max(8) Integer passengerLiftCount,

        @Min(0) @Max(8) Integer gateCount,

        @Min(0) @Max(50) Integer parkingSlotCount,

        @DecimalMin(value = "0.01", message = "Parking slot area must be greater than zero.")

                BigDecimal parkingSlotAreaSqft,

        @Min(50) @Max(200) Integer parkingCarSizePercent,

        @Min(50) @Max(200) Integer shopSizePercent) {}

