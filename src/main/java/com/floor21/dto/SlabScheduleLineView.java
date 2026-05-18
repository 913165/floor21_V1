package com.floor21.dto;

import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.util.List;

/** One row on the payment schedule grid, including computed paid / balance and payment tree. */
public record SlabScheduleLineView(
        BookingPaymentSlab slab,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        List<SlabPaymentSlice> payments) {}
