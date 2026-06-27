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
    void applyToNewBookingSkipsExistingBooking() {
        Booking booking = new Booking();
        booking.setId(java.util.UUID.randomUUID());
        booking.setConsiderationAmt(new BigDecimal("100"));
        BookingTaxDefaults.applyToNewBooking(booking, new Flat());

        assertThat(booking.getTds()).isEqualByComparingTo("0");
        assertThat(booking.getGst()).isEqualByComparingTo("0");
    }
}
