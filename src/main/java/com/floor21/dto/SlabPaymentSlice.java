package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One payment row on a slab (editable on the payment schedule). */
public record SlabPaymentSlice(
        UUID id, LocalDate paymentDate, BigDecimal amount, String reference) {}
