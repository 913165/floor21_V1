package com.floor21.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Platform-admin: add one unit slot to an existing floor row. */
public record FlatAddToFloorDto(
        @NotNull @Min(1) Integer floorNumber,
        @NotBlank String bhkType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
