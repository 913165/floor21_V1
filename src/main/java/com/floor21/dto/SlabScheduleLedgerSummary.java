package com.floor21.dto;

import java.math.BigDecimal;

public record SlabScheduleLedgerSummary(
        BigDecimal totalAmountDue,
        BigDecimal totalReceipts,
        BigDecimal totalBalance,
        BigDecimal totalInterest) {}
