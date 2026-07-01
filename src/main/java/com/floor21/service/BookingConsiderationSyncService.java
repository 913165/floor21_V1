package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.repository.BookingRepository;
import com.floor21.util.BookingTaxDefaults;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps active booking consideration in sync when a flat's base price changes. */
@Service
@RequiredArgsConstructor
public class BookingConsiderationSyncService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabService bookingPaymentSlabService;

    @Transactional
    public void syncActiveBookingsForFlat(UUID flatId, BigDecimal newBasePrice) {
        if (flatId == null || newBasePrice == null || newBasePrice.signum() < 0) {
            return;
        }
        BigDecimal consideration = newBasePrice.setScale(2, RoundingMode.HALF_UP);
        List<Booking> bookings = bookingRepository.findByFlat_Id(flatId);
        if (bookings.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Booking booking : bookings) {
            if (!"ACTIVE".equals(booking.getStatus())) {
                continue;
            }
            booking.setConsiderationAmt(consideration);
            BookingTaxDefaults.recalculateTaxes(booking);
            booking.setUpdatedAt(now);
            bookingRepository.save(booking);
            bookingPaymentSlabService.syncAgreedAmountsFromPercent(booking.getId());
        }
    }
}
