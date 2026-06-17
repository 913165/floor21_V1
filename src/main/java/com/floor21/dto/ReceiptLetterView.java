package com.floor21.dto;

/** Printable receipt letter fields (HTML print + Word export). */
public record ReceiptLetterView(
        String receiptNumber,
        String receiptDateFormatted,
        String instrumentDateFormatted,
        String amountFiguresPrint,
        String amountWordsPrint,
        String payerNamesPrint,
        String paymentInstrumentPrint,
        String drawnOnBankPrint,
        String purposeNarrativePrint,
        String flatNumberPrint,
        String floorPhrasePrint,
        String projectNamePrint,
        String siteAddressPrint,
        String builderCompanyPrint,
        boolean showChequeRealizationDisclaimer) {}
