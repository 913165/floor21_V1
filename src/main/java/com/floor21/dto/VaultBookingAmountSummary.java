package com.floor21.dto;

import java.math.BigDecimal;

/** Slab schedule reference totals and vault payments received (by date, not per slab). */
public record VaultBookingAmountSummary(
        BigDecimal totalFlatAmount,
        BigDecimal totalExtraAmount,
        BigDecimal totalAmount,
        BigDecimal vaultReceivedOnSlabs,
        BigDecimal vaultReceivedExtra,
        BigDecimal vaultReceivedTotal,
        /** Vault deal total (register + extra from vault profile). */
        BigDecimal dealTotal,
        /** {@code dealTotal - vaultReceivedTotal}; null when deal total is unknown. */
        BigDecimal remainingAmount) {}
