package com.floor21.dto;

import java.math.BigDecimal;

/** Platform-admin edit of a single flat after grid generation. */
public record FlatAdminUpdateDto(
        String bhkType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice,
        /** When present (including blank), updates optional column type label; null = leave unchanged. */
        String layoutColumnType) {}
