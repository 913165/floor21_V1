package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SlabPaymentSaveRequest(
        UUID bookingId,
        UUID slabId,
        UUID id,
        LocalDate paymentDate,
        BigDecimal amount,
        String reference) {}
