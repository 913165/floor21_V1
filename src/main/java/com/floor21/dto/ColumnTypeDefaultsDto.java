package com.floor21.dto;

import java.math.BigDecimal;

public record ColumnTypeDefaultsDto(
        int columnNumber,
        String layoutColumnType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
