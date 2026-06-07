package com.floor21.dto;

import java.math.BigDecimal;

public record ColumnTypeDefaultsDto(
        String columnType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
