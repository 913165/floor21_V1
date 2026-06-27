package com.floor21.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sq ft ↔ sq m helpers (same factor as floor21-area-unit.js). */
public final class AreaUnits {

    private static final BigDecimal SQFT_PER_SQM = new BigDecimal("10.763910416709722");

    private AreaUnits() {}

    public static String formatSqMetersFromSqft(BigDecimal sqft) {
        if (sqft == null || sqft.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return sqft.divide(SQFT_PER_SQM, 2, RoundingMode.HALF_UP).toPlainString();
    }
}
