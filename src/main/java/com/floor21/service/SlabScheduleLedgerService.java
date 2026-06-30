package com.floor21.service;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.dto.SlabLedgerRowType;
import com.floor21.dto.SlabScheduleLedgerRow;
import com.floor21.dto.SlabScheduleLedgerSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlabScheduleLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    private final ReceiptSlabAllocationService receiptSlabAllocationService;
    private final BookingPaymentSlabService bookingPaymentSlabService;

    /** Ledger from milestone definitions + live receipt waterfall (not from stored slab payments). */
    @Transactional
    public List<SlabScheduleLedgerRow> buildLedger(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        BigDecimal interestRate = BookingPaymentSlabService.effectiveInterestRatePercent(booking);
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listSlabsForPaymentLedger(bookingId);
        return buildLedgerRows(slabs, bySlab, interestRate, booking.getBookingDate());
    }

    /** Read-only ledger for platform admin (no milestone sync on load). */
    @Transactional(readOnly = true)
    public List<SlabScheduleLedgerRow> buildLedgerReadOnly(UUID bookingId, UUID builderId) {
        Booking booking = bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
        BigDecimal interestRate = BookingPaymentSlabService.effectiveInterestRatePercent(booking);
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId, builderId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listSlabsForPaymentLedgerReadOnly(bookingId, builderId);
        return buildLedgerRows(slabs, bySlab, interestRate, booking.getBookingDate());
    }

    List<SlabScheduleLedgerRow> buildLedgerRows(
            List<BookingPaymentSlab> slabs,
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab,
            BigDecimal annualRatePercent,
            LocalDate bookingDate) {
        LocalDate today = LocalDate.now();
        List<SlabScheduleLedgerRow> rows = new ArrayList<>();
        Map<UUID, BigDecimal> receiptTotals = receiptTotalsById(bySlab);
        Map<UUID, BigDecimal> gstTotals = gstTotalsById(bySlab);
        Map<UUID, Integer> firstSlabForReceipt = firstSlabIndexByReceipt(slabs, bySlab);

        for (int slabIndex = 0; slabIndex < slabs.size(); slabIndex++) {
            BookingPaymentSlab slab = slabs.get(slabIndex);
            BigDecimal due = SlabReceiptWaterfall.slabDue(slab);
            List<ReceiptSlabAllocationSlice> payments =
                    bySlab.getOrDefault(slab.getId(), List.of());
            LocalDate effectiveDueDate =
                    BookingPaymentSlabService.clampDueDateToBookingDate(
                            slab.getDueDate(), bookingDate);
            boolean dueDateReached =
                    effectiveDueDate == null || !effectiveDueDate.isAfter(today);

            rows.add(
                    new SlabScheduleLedgerRow(
                            SlabLedgerRowType.SLAB_TOTAL,
                            effectiveDueDate,
                            slab.getMilestoneLabel(),
                            null,
                            due,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));

            BigDecimal runningPaid = ZERO;
            for (ReceiptSlabAllocationSlice payment : payments) {
                boolean firstSlabForThisReceipt =
                        payment.receiptId() != null
                                && Integer.valueOf(slabIndex)
                                        .equals(firstSlabForReceipt.get(payment.receiptId()));
                BigDecimal displayAmount =
                        firstSlabForThisReceipt
                                ? receiptTotals.getOrDefault(payment.receiptId(), payment.amount())
                                : payment.amount();
                BigDecimal sliceGst = payment.gstAmount() != null ? payment.gstAmount() : ZERO;
                BigDecimal displayGst =
                        firstSlabForThisReceipt
                                ? gstTotals.getOrDefault(payment.receiptId(), sliceGst)
                                : sliceGst;
                runningPaid = runningPaid.add(displayAmount);
                BigDecimal balance = due.subtract(runningPaid);
                if (!firstSlabForThisReceipt) {
                    BigDecimal forward =
                            laterAllocationForReceipt(
                                    payment.receiptId(), slabIndex, slabs, bySlab);
                    if (forward.compareTo(ZERO) > 0) {
                        balance = balance.subtract(forward);
                    } else {
                        balance = balance.max(ZERO);
                    }
                }
                LocalDate paymentDate =
                        payment.paymentDate() != null ? payment.paymentDate() : today;
                int days =
                        effectiveDueDate != null
                                ? (int) Math.max(0, ChronoUnit.DAYS.between(effectiveDueDate, paymentDate))
                                : 0;
                BigDecimal interestPrincipal = payment.amount();
                BigDecimal interest = simpleInterest(interestPrincipal, annualRatePercent, days);
                String info =
                        days > 0 && interestPrincipal.compareTo(ZERO) > 0
                                ? buildReceiptInterestInfo(
                                        interest, days, interestPrincipal, annualRatePercent)
                                : null;
                rows.add(
                        new SlabScheduleLedgerRow(
                                SlabLedgerRowType.RECEIPT,
                                payment.paymentDate(),
                                null,
                                payment.chequeLabel(),
                                null,
                                displayAmount,
                                displayGst.compareTo(ZERO) > 0 ? displayGst : null,
                                balance,
                                days > 0 ? days : null,
                                interest.compareTo(ZERO) > 0 ? interest : null,
                                info,
                                payment.remark(),
                                payment.receiptId()));
            }

            BigDecimal outstanding = due.subtract(runningPaid).max(ZERO);
            if (dueDateReached && outstanding.compareTo(ZERO) > 0) {
                int days =
                        effectiveDueDate != null
                                ? (int) Math.max(0, ChronoUnit.DAYS.between(effectiveDueDate, today))
                                : 0;
                BigDecimal interest = simpleInterest(outstanding, annualRatePercent, days);
                String info = buildTodayInfo(interest, days, outstanding);
                rows.add(
                        new SlabScheduleLedgerRow(
                                SlabLedgerRowType.TODAY,
                                today,
                                "Today",
                                null,
                                null,
                                outstanding,
                                null,
                                ZERO,
                                days > 0 ? days : null,
                                interest.compareTo(ZERO) > 0 ? interest : null,
                                info,
                                null,
                                null));
            }
        }
        return rows;
    }

    /**
     * Receipt amount allocated to later slabs (display only). When a receipt clears the current slab
     * and carries surplus forward, later slab rows show the forwarded slice only.
     */
    private static BigDecimal laterAllocationForReceipt(
            UUID receiptId,
            int slabIndex,
            List<BookingPaymentSlab> slabs,
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        if (receiptId == null) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        for (int i = slabIndex + 1; i < slabs.size(); i++) {
            for (ReceiptSlabAllocationSlice slice :
                    bySlab.getOrDefault(slabs.get(i).getId(), List.of())) {
                if (receiptId.equals(slice.receiptId())) {
                    total = total.add(slice.amount());
                }
            }
        }
        return total;
    }

    private static Map<UUID, BigDecimal> gstTotalsById(
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (List<ReceiptSlabAllocationSlice> slices : bySlab.values()) {
            for (ReceiptSlabAllocationSlice slice : slices) {
                if (slice.receiptId() == null) {
                    continue;
                }
                BigDecimal gst = slice.gstAmount() != null ? slice.gstAmount() : ZERO;
                totals.merge(slice.receiptId(), gst, BigDecimal::add);
            }
        }
        return totals;
    }

    private static Map<UUID, BigDecimal> receiptTotalsById(
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        Map<UUID, BigDecimal> totals = new HashMap<>();
        for (List<ReceiptSlabAllocationSlice> slices : bySlab.values()) {
            for (ReceiptSlabAllocationSlice slice : slices) {
                if (slice.receiptId() == null) {
                    continue;
                }
                totals.merge(slice.receiptId(), slice.amount(), BigDecimal::add);
            }
        }
        return totals;
    }

    private static Map<UUID, Integer> firstSlabIndexByReceipt(
            List<BookingPaymentSlab> slabs, Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        Map<UUID, Integer> first = new HashMap<>();
        for (int i = 0; i < slabs.size(); i++) {
            for (ReceiptSlabAllocationSlice slice :
                    bySlab.getOrDefault(slabs.get(i).getId(), List.of())) {
                first.putIfAbsent(slice.receiptId(), i);
            }
        }
        return first;
    }

    @Transactional(readOnly = true)
    public SlabScheduleLedgerSummary summarizeLedger(List<SlabScheduleLedgerRow> rows) {
        BigDecimal totalDue = ZERO;
        BigDecimal totalReceipt = ZERO;
        BigDecimal totalGst = ZERO;
        BigDecimal totalBalance = ZERO;
        BigDecimal totalInterest = ZERO;
        Set<UUID> receiptIdsCounted = new HashSet<>();
        for (SlabScheduleLedgerRow row : rows) {
            if (row.rowType() == SlabLedgerRowType.SLAB_TOTAL && row.amountDue() != null) {
                totalDue = totalDue.add(row.amountDue());
            }
            if (row.rowType() == SlabLedgerRowType.RECEIPT && row.receiptAmount() != null) {
                UUID receiptId = row.receiptId();
                if (receiptId == null || receiptIdsCounted.add(receiptId)) {
                    totalReceipt = totalReceipt.add(row.receiptAmount());
                    if (row.gstAmount() != null) {
                        totalGst = totalGst.add(row.gstAmount());
                    }
                }
            }
            if (row.rowType() == SlabLedgerRowType.TODAY && row.receiptAmount() != null) {
                totalBalance = totalBalance.add(row.receiptAmount());
            }
            if ((row.rowType() == SlabLedgerRowType.RECEIPT
                            || row.rowType() == SlabLedgerRowType.TODAY)
                    && row.interest() != null) {
                totalInterest = totalInterest.add(row.interest());
            }
        }
        return new SlabScheduleLedgerSummary(totalDue, totalReceipt, totalGst, totalBalance, totalInterest);
    }

    private static String buildReceiptInterestInfo(
            BigDecimal interest, int days, BigDecimal principal, BigDecimal annualRatePercent) {
        return "Rs."
                + formatInfoAmount(interest)
                + " as interest for "
                + days
                + " days for Rs."
                + formatInfoAmount(principal)
                + " @ "
                + annualRatePercent.stripTrailingZeros().toPlainString()
                + " %";
    }

    private static String buildTodayInfo(BigDecimal interest, int days, BigDecimal outstanding) {
        StringBuilder info = new StringBuilder();
        if (interest.compareTo(ZERO) > 0 && days > 0) {
            info.append("Rs.")
                    .append(formatInfoAmount(interest))
                    .append(" as interest for ")
                    .append(days)
                    .append(" days for Rs.")
                    .append(formatInfoAmount(outstanding))
                    .append(". ");
        }
        info.append("Note: The amount ")
                .append(formatInfoAmount(outstanding))
                .append(" is not yet received");
        return info.toString();
    }

    private static String formatInfoAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal simpleInterest(BigDecimal principal, BigDecimal ratePercent, int days) {
        if (days <= 0 || principal.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return principal
                .multiply(ratePercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(DAYS_IN_YEAR.multiply(new BigDecimal("100")), 0, RoundingMode.HALF_UP);
    }
}
