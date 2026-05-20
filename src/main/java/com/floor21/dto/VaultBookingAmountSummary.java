package com.floor21.dto;

import java.math.BigDecimal;

/** Vault deal amounts, income, and expenses (by date, independent of slab schedule). */
public record VaultBookingAmountSummary(
        BigDecimal totalFlatAmount,
        BigDecimal totalExtraAmount,
        BigDecimal totalAmount,
        BigDecimal vaultReceivedOnSlabs,
        BigDecimal vaultReceivedExtra,
        BigDecimal vaultReceivedTotal,
        BigDecimal vaultExpenseTotal,
        /** Income minus expenses. */
        BigDecimal netVaultBalance,
        /** Vault deal total (register + extra from vault profile). */
        BigDecimal dealTotal,
        /** {@code dealTotal - vaultReceivedTotal}; null when deal total is unknown. */
        BigDecimal remainingAmount) {}
