package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookingPaymentSlabDueDateTest {

    @Test
    void clampDueDateToBookingDate_leavesNullAndFutureDates() {
        LocalDate booking = LocalDate.of(2026, 6, 28);
        assertThat(BookingPaymentSlabService.clampDueDateToBookingDate(null, booking)).isNull();
        assertThat(BookingPaymentSlabService.clampDueDateToBookingDate(booking, booking))
                .isEqualTo(booking);
        assertThat(
                        BookingPaymentSlabService.clampDueDateToBookingDate(
                                LocalDate.of(2026, 7, 1), booking))
                .isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void clampDueDateToBookingDate_raisesPastDatesToBookingDate() {
        LocalDate booking = LocalDate.of(2026, 6, 28);
        assertThat(
                        BookingPaymentSlabService.clampDueDateToBookingDate(
                                LocalDate.of(2026, 3, 10), booking))
                .isEqualTo(booking);
        assertThat(
                        BookingPaymentSlabService.clampDueDateToBookingDate(
                                LocalDate.of(2026, 6, 24), booking))
                .isEqualTo(booking);
    }
}
