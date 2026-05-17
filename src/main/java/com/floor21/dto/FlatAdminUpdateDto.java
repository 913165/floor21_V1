package com.floor21.dto;

import java.math.BigDecimal;

/** Platform-admin edit of a single flat after grid generation. */
public record FlatAdminUpdateDto(String bhkType, BigDecimal areaSqft, BigDecimal basePrice) {}
