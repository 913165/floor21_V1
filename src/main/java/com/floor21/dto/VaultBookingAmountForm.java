package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Editable deal amounts for a booking on the vault page (vault-only; not synced to booking/slabs). */
@Getter
@Setter
public class VaultBookingAmountForm {

    private UUID bookingId;
    private BigDecimal totalConsideration;
    private BigDecimal registerValue;
    private BigDecimal extraAmount;
}
