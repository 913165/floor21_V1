package com.floor21.controller;

import com.floor21.entity.Booking;
import com.floor21.repository.BookingRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Helpers for milestone-area building/booking pickers shared across controllers. */
final class MilestoneNavSupport {

    private MilestoneNavSupport() {}

    static UUID inferBuildingId(BookingRepository bookingRepository, UUID bookingId, UUID builderId) {
        if (bookingId == null || builderId == null) {
            return null;
        }
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .map(MilestoneNavSupport::buildingIdOf)
                .orElse(null);
    }

    static UUID buildingIdOf(Booking booking) {
        if (booking == null || booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return null;
        }
        return booking.getFlat().getBuilding().getId();
    }

    static List<Booking> ensureSelectedBooking(List<Booking> bookings, Booking selected) {
        if (selected == null) {
            return bookings;
        }
        boolean found =
                bookings.stream().anyMatch(b -> selected.getId().equals(b.getId()));
        if (found) {
            return bookings;
        }
        List<Booking> merged = new ArrayList<>(bookings);
        merged.add(selected);
        merged.sort(
                Comparator.comparing(
                        (Booking b) ->
                                b.getFlat() != null && b.getFlat().getFlatNumber() != null
                                        ? b.getFlat().getFlatNumber()
                                        : "",
                        String.CASE_INSENSITIVE_ORDER));
        return merged;
    }
}
