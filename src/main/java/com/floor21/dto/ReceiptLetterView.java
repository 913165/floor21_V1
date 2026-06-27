package com.floor21.dto;

import java.util.List;

/** Printable receipt letter fields (HTML print + Word export). */
public record ReceiptLetterView(
        String receiptDateOrdinal,
        String amountFiguresPrint,
        String amountWordsPrint,
        String payerNamesPrint,
        String paymentWayPrint,
        String totalConsiderationFiguresPrint,
        String totalConsiderationWordsPrint,
        String unitDescriptionPrint,
        String projectNamePrint,
        String landAddressPrint,
        List<ReceiptPaymentTableRow> paymentTableRows,
        String placePrint,
        String builderCompanyPrint,
        boolean showChequeRealizationDisclaimer,
        boolean combinedPrint) {}
