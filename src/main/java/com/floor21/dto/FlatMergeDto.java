package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Remove one flat and apply layout details to the flat being kept (e.g. two 2BHK → one 4BHK). */
public record FlatMergeDto(
        UUID removeFlatId,
        String bhkType,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        BigDecimal basePrice) {}
