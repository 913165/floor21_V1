package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardEligibleSlabStatsTest {

  @Test
  void countsEligibleIssuedAndDueAmountThroughLatestDueSlab() {
    LocalDate bookingDate = LocalDate.of(2025, 1, 1);
    Booking booking = new Booking();
    booking.setBookingDate(bookingDate);

    BookingPaymentSlab sent =
            slab(booking, LocalDate.of(2025, 6, 1), "100000", "0", 0, true);
    BookingPaymentSlab pending =
            slab(booking, LocalDate.now().minusDays(1), "200000", "5000", 1, false);
    BookingPaymentSlab future =
            slab(booking, LocalDate.now().plusMonths(2), "300000", "0", 2, false);

    DashboardEligibleSlabStats stats =
            DashboardEligibleSlabStats.fromSlabs(List.of(sent, pending, future), bookingDate);

    assertThat(stats.eligibleCount()).isEqualTo(2L);
    assertThat(stats.issuedCount()).isEqualTo(1L);
    assertThat(stats.dlPendingCount()).isEqualTo(1L);
    assertThat(stats.dueAmount()).isEqualByComparingTo("305000");
  }

  private static BookingPaymentSlab slab(
          Booking booking,
          LocalDate dueDate,
          String agreed,
          String extra,
          int sortOrder,
          boolean sent) {
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setBooking(booking);
    slab.setDueDate(dueDate);
    slab.setAgreedAmount(new BigDecimal(agreed));
    slab.setExtraAmount(new BigDecimal(extra));
    slab.setSortOrder(sortOrder);
    slab.setDemandLetterSentToClient(sent);
    return slab;
  }
}
