package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingPaymentSlabServiceDisplaySlabsTest {

  @Test
  void displaySlabsForStatsDedupesMilestoneLabelsLikePaymentSchedule() {
    Booking booking = new Booking();
    booking.setId(UUID.randomUUID());
    booking.setBookingDate(LocalDate.of(2025, 1, 1));

    BookingPaymentSlab keeper =
            slab(booking, "Booking amount", LocalDate.of(2025, 6, 1), true, 0);
    BookingPaymentSlab duplicate =
            slab(booking, "Booking amount", LocalDate.of(2025, 6, 1), false, 1);

    List<BookingPaymentSlab> display =
            BookingPaymentSlabService.displaySlabsForStats(List.of(keeper, duplicate));

    assertThat(display).hasSize(1);
    assertThat(display.getFirst().isDemandLetterSentToClient()).isTrue();
  }

  @Test
  void displaySlabsForStatsStopsAtFirstSlabWithoutDueDate() {
    Booking booking = new Booking();
    booking.setBookingDate(LocalDate.of(2025, 1, 1));

    BookingPaymentSlab due =
            slab(booking, "Slab 1", LocalDate.of(2025, 6, 1), false, 0);
    BookingPaymentSlab noDue = slab(booking, "Slab 2", null, false, 1);
    BookingPaymentSlab later =
            slab(booking, "Slab 3", LocalDate.of(2025, 12, 1), false, 2);

    List<BookingPaymentSlab> display =
            BookingPaymentSlabService.displaySlabsForStats(List.of(due, noDue, later));

    assertThat(display).containsExactly(due);
  }

  private static BookingPaymentSlab slab(
          Booking booking,
          String label,
          LocalDate dueDate,
          boolean sent,
          int sortOrder) {
    BookingPaymentSlab slab = new BookingPaymentSlab();
    slab.setBooking(booking);
    slab.setMilestoneLabel(label);
    slab.setDueDate(dueDate);
    slab.setAgreedAmount(BigDecimal.ONE);
    slab.setSortOrder(sortOrder);
    slab.setDemandLetterSentToClient(sent);
    return slab;
  }
}
