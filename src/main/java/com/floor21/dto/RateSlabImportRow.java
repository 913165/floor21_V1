package com.floor21.dto;

import java.math.BigDecimal;

/** One row parsed from a milestone settings Excel import. */
public record RateSlabImportRow(int sortOrder, String slabName, BigDecimal suggestedPercent, boolean active) {}
