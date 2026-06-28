package com.floor21.dto;

/** Printable receipt fields (HTML print + Word export). */
public record ReceiptLetterView(
        String receiptNumberPrint,
        String receiptDateShort,
        String payerNamesPrint,
        String amountFiguresPrint,
        String amountWordsPrint,
        String instrumentNarrativePrint,
        String paymentPurposePrint,
        String flatNumberPrint,
        String floorPhrasePrint,
        String projectNamePrint,
        String siteAddressPrint,
        String builderCompanyPrint,
        String footerAddressPrint,
        String footerPhonePrint,
        String footerEmailPrint,
        boolean showChequeRealizationDisclaimer,
        boolean combinedPrint) {}
