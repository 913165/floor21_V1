package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row on the slab payment schedule ledger (milestone, receipt slice, or today/outstanding). */
public record SlabScheduleLedgerRow(
        SlabLedgerRowType rowType,
        LocalDate date,
        String slabLabel,
        String chequeLabel,
        BigDecimal amountDue,
        BigDecimal receiptAmount,
        BigDecimal gstAmount,
        BigDecimal balance,
        Integer days,
        BigDecimal interest,
        String info,
        String remark,
        UUID receiptId,
        UUID paymentSlabId,
        Boolean demandLetterSentToClient) {}
