package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AllotteeLedgerRow(
        AllotteeLedgerRowType rowType,
        Integer serialNo,
        LocalDate date,
        String receiptNumber,
        String narrationTitle,
        String narrationDetail,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance,
        String balanceSide) {

    public String rowCssClass() {
        return switch (rowType) {
            case OPENING -> "allottee-ledger-doc__row--opening";
            case CLOSING -> "allottee-ledger-doc__row--closing fw-semibold";
            default -> "";
        };
    }
}
