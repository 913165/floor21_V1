package com.floor21.util;

import com.floor21.entity.Booking;
import com.floor21.entity.Flat;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Default booking tax amounts: TDS 1% and GST 5% of consideration (matches demand letters). */
public final class BookingTaxDefaults {

    public static final int TDS_PERCENT = 1;
    public static final int GST_PERCENT = 5;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private BookingTaxDefaults() {}

    public static BigDecimal percentOf(BigDecimal base, int percent) {
        if (base == null || base.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return base.multiply(BigDecimal.valueOf(percent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Fills consideration (from flat base price), TDS, and GST on a new booking when still blank. */
    public static void applyToNewBooking(Booking booking, Flat flat) {
        if (booking == null || booking.getId() != null) {
            return;
        }
        BigDecimal consideration = booking.getConsiderationAmt();
        if ((consideration == null || consideration.compareTo(ZERO) == 0)
                && flat != null
                && flat.getBasePrice() != null
                && flat.getBasePrice().compareTo(ZERO) > 0) {
            consideration = flat.getBasePrice();
            booking.setConsiderationAmt(consideration);
        }
        if (consideration == null || consideration.compareTo(ZERO) <= 0) {
            return;
        }
        if (booking.getTds() == null || booking.getTds().compareTo(ZERO) == 0) {
            booking.setTds(percentOf(consideration, TDS_PERCENT));
        }
        if (booking.getGst() == null || booking.getGst().compareTo(ZERO) == 0) {
            booking.setGst(percentOf(consideration, GST_PERCENT));
        }
    }
}
