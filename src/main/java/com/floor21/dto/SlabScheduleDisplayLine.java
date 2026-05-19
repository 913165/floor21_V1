package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One read-only row on the payment schedule page (platform milestone + booking amounts). */
public record SlabScheduleDisplayLine(
        int serialNo,
        String milestoneLabel,
        BigDecimal percent,
        LocalDate dueDate,
        BigDecimal agreedAmount,
        BigDecimal extraAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAmount) {}
