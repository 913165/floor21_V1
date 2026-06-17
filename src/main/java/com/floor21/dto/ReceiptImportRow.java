package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptImportRow(
        int excelRow,
        LocalDate receiptDate,
        String receiptNumber,
        BigDecimal consideration,
        BigDecimal extraCharges,
        BigDecimal interestAgreement,
        BigDecimal interestGst,
        BigDecimal tds,
        BigDecimal gstComponent,
        String paymentMode,
        String chequeNo,
        LocalDate chequeDate,
        String bankName,
        String paidBy,
        String enteredBy,
        String remarks,
        boolean dishonoured) {}
