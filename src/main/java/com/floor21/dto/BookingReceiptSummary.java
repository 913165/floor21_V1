package com.floor21.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BookingReceiptSummary {

    BigDecimal agreementTotal;
    BigDecimal agreementReceived;
    BigDecimal agreementBalance;

    boolean showGstRow;
    BigDecimal gstTotal;
    BigDecimal gstReceived;
    BigDecimal gstBalance;
}
