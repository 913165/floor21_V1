package com.floor21.dto;

import java.math.BigDecimal;

public record SlabScheduleLedgerSummary(
        BigDecimal totalAmountDue,
        BigDecimal totalReceipts,
        BigDecimal totalGst,
        BigDecimal totalBalance,
        BigDecimal totalInterest) {}
