package com.floor21.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ParkingFloorConfigDto(
        @NotNull @Min(1) @Max(200) Integer slotCount) {}
