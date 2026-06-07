package com.floor21.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ColumnTypeDefaultsSaveDto(
        @NotNull @Min(1) Integer columnNumber,
        String layoutColumnType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
