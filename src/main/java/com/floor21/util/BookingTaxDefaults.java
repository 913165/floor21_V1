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
        applyMissingTaxes(booking);
    }

    /** True when consideration is set but TDS, GST, or final amount are still zero. */
    public static boolean needsTaxDefaults(Booking booking) {
        if (booking == null) {
            return false;
        }
        BigDecimal consideration = booking.getConsiderationAmt();
        if (consideration == null || consideration.compareTo(ZERO) <= 0) {
            return false;
        }
        return isZeroOrNull(booking.getTds())
                || isZeroOrNull(booking.getGst())
                || isZeroOrNull(booking.getFinalAmount());
    }

    /** Fills blank TDS, GST, and final amount from consideration (1%, 5%, consideration + GST). */
    public static void applyMissingTaxes(Booking booking) {
        if (booking == null) {
            return;
        }
        BigDecimal consideration = booking.getConsiderationAmt();
        if (consideration == null || consideration.compareTo(ZERO) <= 0) {
            return;
        }
        if (isZeroOrNull(booking.getTds())) {
            booking.setTds(percentOf(consideration, TDS_PERCENT));
        }
        if (isZeroOrNull(booking.getGst())) {
            booking.setGst(percentOf(consideration, GST_PERCENT));
        }
        if (isZeroOrNull(booking.getFinalAmount())) {
            BigDecimal gst = booking.getGst() != null ? booking.getGst() : ZERO;
            booking.setFinalAmount(consideration.add(gst));
        }
    }

    /** Overwrites TDS, GST, and final amount from consideration. */
    public static void recalculateTaxes(Booking booking) {
        if (booking == null) {
            return;
        }
        BigDecimal consideration = booking.getConsiderationAmt();
        if (consideration == null || consideration.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Consideration is required to calculate TDS and GST");
        }
        BigDecimal gst = percentOf(consideration, GST_PERCENT);
        booking.setTds(percentOf(consideration, TDS_PERCENT));
        booking.setGst(gst);
        booking.setFinalAmount(consideration.add(gst));
    }

    private static boolean isZeroOrNull(BigDecimal value) {
        return value == null || value.compareTo(ZERO) == 0;
    }
}
