package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SlabPaymentSaveResponse(
        UUID id,
        BigDecimal slabDue,
        BigDecimal slabPaid,
        BigDecimal slabBalance,
        BigDecimal totalPaid,
        BigDecimal totalBalance) {}
