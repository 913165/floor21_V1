package com.floor21.dto;

import java.math.BigDecimal;

/** One row parsed from a payment milestone Excel import. */
public record PaymentMilestoneImportRow(int sortOrder, String milestoneLabel, BigDecimal suggestedPercent) {}
