package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceDueTillLatestSlabTest {

  @Test
  void sumsSlabsThroughLatestDueDateAndStopsAtFutureOrUndated() {
    LocalDate bookingDate = LocalDate.of(2025, 1, 1);
    Booking booking = new Booking();
    booking.setBookingDate(bookingDate);

    BookingPaymentSlab past =
            slab(booking, LocalDate.of(2025, 6, 1), "100000", "0", 0);
    BookingPaymentSlab latestDue =
            slab(booking, LocalDate.now().minusDays(1), "200000", "5000", 1);
    BookingPaymentSlab future =
            slab(booking, LocalDate.now().plusMonths(2), "300000", "0", 2);
    BookingPaymentSlab undated = slab(booking, null, "400000", "0", 3);

    BigDecimal total =
            DashboardService.dueTillLatestSlabForBooking(
                    List.of(past, latestDue, future, undated), bookingDate);

    assertThat(total).isEqualByComparingTo("305000");
  }

  @Test
  void returnsZeroWhenNoSlabDueDateHasPassed() {
    LocalDate bookingDate = LocalDate.of(2025, 1, 1);
    Booking booking = new Booking();
    booking.setBookingDate(bookingDate);

    BookingPaymentSlab future =
            slab(booking, LocalDate.now().plusMonths(1), "100000", "0", 0);

    BigDecimal total =
            DashboardService.dueTillLatestSlabForBooking(List.of(future), bookingDate);

    assertThat(total).isEqualByComparingTo("0");
  }

  private static BookingPaymentSlab slab(
          Booking booking,
          LocalDate dueDate,
          String agreed,
          String extra,
          int sortOrder) {
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setBooking(booking);
    slab.setDueDate(dueDate);
    slab.setAgreedAmount(new BigDecimal(agreed));
    slab.setExtraAmount(new BigDecimal(extra));
    slab.setSortOrder(sortOrder);
    return slab;
  }
}
