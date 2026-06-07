package com.floor21.dto;

import java.math.BigDecimal;

public record UnitTypeDefaultsDto(
        String bhkType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
