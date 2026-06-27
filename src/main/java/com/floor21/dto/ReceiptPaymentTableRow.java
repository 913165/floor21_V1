package com.floor21.dto;

/** One line on the printable receipt payment table. */
public record ReceiptPaymentTableRow(
        int serialNo, String dateFormatted, String instrumentDetail, String amountDisplay) {}
