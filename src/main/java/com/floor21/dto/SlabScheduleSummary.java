package com.floor21.dto;

import java.math.BigDecimal;

/** Totals for a booking's payment slab schedule grid. */
public record SlabScheduleSummary(
        BigDecimal totalAgreedAmount,
        BigDecimal totalExtraAmount,
        BigDecimal totalAmount,
        BigDecimal totalPercent) {}
