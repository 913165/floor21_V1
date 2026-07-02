package com.floor21.service;

import com.floor21.entity.BookingPaymentSlab;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Counts and amounts for milestone slabs whose due date has passed (payment schedule scope). */
final class DashboardEligibleSlabStats {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final long eligibleCount;
    private final long issuedCount;
    private final BigDecimal dueAmount;

    private DashboardEligibleSlabStats(long eligibleCount, long issuedCount, BigDecimal dueAmount) {
        this.eligibleCount = eligibleCount;
        this.issuedCount = issuedCount;
        this.dueAmount = dueAmount;
    }

    long eligibleCount() {
        return eligibleCount;
    }

    long issuedCount() {
        return issuedCount;
    }

    long dlPendingCount() {
        return Math.max(0L, eligibleCount - issuedCount);
    }

    BigDecimal dueAmount() {
        return dueAmount;
    }

    static DashboardEligibleSlabStats fromSlabs(
            List<BookingPaymentSlab> slabsInOrder, LocalDate bookingDate) {
        LocalDate today = LocalDate.now();
        long total = 0L;
        long issued = 0L;
        BigDecimal due = ZERO;
        for (BookingPaymentSlab slab : slabsInOrder) {
            if (slab.getDueDate() == null) {
                break;
            }
            LocalDate effective =
                    BookingPaymentSlabService.clampDueDateToBookingDate(
                            slab.getDueDate(), bookingDate);
            if (effective == null || effective.isAfter(today)) {
                break;
            }
            total++;
            if (slab.isDemandLetterSentToClient()) {
                issued++;
            }
            due = due.add(SlabReceiptWaterfall.slabDue(slab));
        }
        return new DashboardEligibleSlabStats(total, issued, due);
    }
}
