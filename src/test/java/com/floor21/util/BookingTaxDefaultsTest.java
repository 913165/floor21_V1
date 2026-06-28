package com.floor21.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.entity.Booking;
import com.floor21.entity.Flat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BookingTaxDefaultsTest {

    @Test
    void percentOfUsesOneAndFivePercent() {
        assertThat(BookingTaxDefaults.percentOf(new BigDecimal("1157000"), 1))
                .isEqualByComparingTo("11570.00");
        assertThat(BookingTaxDefaults.percentOf(new BigDecimal("1157000"), 5))
                .isEqualByComparingTo("57850.00");
    }

    @Test
    void applyToNewBookingUsesFlatBasePriceAndTaxDefaults() {
        Flat flat = new Flat();
        flat.setBasePrice(new BigDecimal("1000000"));

        Booking booking = new Booking();
        BookingTaxDefaults.applyToNewBooking(booking, flat);

        assertThat(booking.getConsiderationAmt()).isEqualByComparingTo("1000000");
        assertThat(booking.getTds()).isEqualByComparingTo("10000.00");
        assertThat(booking.getGst()).isEqualByComparingTo("50000.00");
    }

    @Test
    void applyMissingTaxesFillsBlankFieldsFromConsideration() {
        Booking booking = new Booking();
        booking.setConsiderationAmt(new BigDecimal("22000000"));

        BookingTaxDefaults.applyMissingTaxes(booking);

        assertThat(booking.getTds()).isEqualByComparingTo("220000.00");
        assertThat(booking.getGst()).isEqualByComparingTo("1100000.00");
        assertThat(booking.getFinalAmount()).isEqualByComparingTo("23100000.00");
    }

    @Test
    void needsTaxDefaultsWhenConsiderationSetButTaxesBlank() {
        Booking booking = new Booking();
        booking.setConsiderationAmt(new BigDecimal("1000000"));
        assertThat(BookingTaxDefaults.needsTaxDefaults(booking)).isTrue();

        booking.setGst(new BigDecimal("50000"));
        booking.setTds(new BigDecimal("10000"));
        booking.setFinalAmount(new BigDecimal("1050000"));
        assertThat(BookingTaxDefaults.needsTaxDefaults(booking)).isFalse();
    }

    @Test
    void recalculateTaxesOverwritesExistingValues() {
        Booking booking = new Booking();
        booking.setConsiderationAmt(new BigDecimal("1000000"));
        booking.setTds(new BigDecimal("1"));
        booking.setGst(new BigDecimal("1"));

        BookingTaxDefaults.recalculateTaxes(booking);

        assertThat(booking.getTds()).isEqualByComparingTo("10000.00");
        assertThat(booking.getGst()).isEqualByComparingTo("50000.00");
        assertThat(booking.getFinalAmount()).isEqualByComparingTo("1050000.00");
    }
}
