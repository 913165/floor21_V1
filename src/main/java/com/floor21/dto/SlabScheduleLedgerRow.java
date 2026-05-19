package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row on the slab payment schedule ledger (milestone, receipt slice, or today/outstanding). */
public record SlabScheduleLedgerRow(
        SlabLedgerRowType rowType,
        LocalDate date,
        String slabLabel,
        BigDecimal amountDue,
        BigDecimal receiptAmount,
        BigDecimal balance,
        Integer days,
        BigDecimal interest,
        String info,
        UUID receiptId) {}
