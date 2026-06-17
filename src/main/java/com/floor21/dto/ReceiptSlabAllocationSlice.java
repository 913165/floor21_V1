package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** In-memory receipt amount applied to one payment slab (not persisted). */
public record ReceiptSlabAllocationSlice(
        UUID slabId,
        UUID receiptId,
        LocalDate paymentDate,
        BigDecimal amount,
        String reference,
        String remark,
        String chequeLabel) {}
