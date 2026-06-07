package com.floor21.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record UnitTypeDefaultsSaveDto(
        @NotBlank String bhkType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
