package com.floor21.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Percentage display for milestone and schedule grids. */
public final class PercentageFormatter {

    private PercentageFormatter() {}

    /** e.g. {@code 12.50 %}; em dash when null. */
    public static String formatDisplay(BigDecimal percent) {
        if (percent == null) {
            return "\u2014";
        }
        return percent.setScale(2, RoundingMode.HALF_UP).toPlainString() + " %";
    }
}
