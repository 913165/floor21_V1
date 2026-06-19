package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AllotteeLedgerView(
        String allotteeName,
        String panNumber,
        String unitLabel,
        BigDecimal agreementValue,
        LocalDate periodFrom,
        LocalDate periodTo,
        String financialYearLabel,
        BigDecimal totalDemanded,
        BigDecimal totalReceived,
        BigDecimal balanceReceivable,
        BigDecimal gstCollected,
        List<AllotteeLedgerRow> rows) {}
