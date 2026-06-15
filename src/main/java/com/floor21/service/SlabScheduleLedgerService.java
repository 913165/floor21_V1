package com.floor21.service;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.dto.SlabLedgerRowType;
import com.floor21.dto.SlabScheduleLedgerRow;
import com.floor21.dto.SlabScheduleLedgerSummary;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.util.IndianRupeesFormatter;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlabScheduleLedgerService {

    private static final BigDecimal DEFAULT_ANNUAL_RATE_PERCENT = new BigDecimal("15");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    private final ReceiptSlabAllocationService receiptSlabAllocationService;
    private final BookingPaymentSlabService bookingPaymentSlabService;

    /** Ledger from milestone definitions + live receipt waterfall (not from stored slab payments). */
    @Transactional
    public List<SlabScheduleLedgerRow> buildLedger(UUID bookingId) {
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listUniqueSlabsForSchedule(bookingId);
        return buildLedgerRows(slabs, bySlab);
    }

    /** Read-only ledger for platform admin (no milestone sync on load). */
    @Transactional(readOnly = true)
    public List<SlabScheduleLedgerRow> buildLedgerReadOnly(UUID bookingId, UUID builderId) {
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId, builderId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listUniqueSlabsForScheduleReadOnly(bookingId, builderId);
        return buildLedgerRows(slabs, bySlab);
    }

    private List<SlabScheduleLedgerRow> buildLedgerRows(
            List<BookingPaymentSlab> slabs, Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        LocalDate today = LocalDate.now();
        List<SlabScheduleLedgerRow> rows = new ArrayList<>();

        for (BookingPaymentSlab slab : slabs) {
            BigDecimal due = SlabReceiptWaterfall.slabDue(slab);
            List<ReceiptSlabAllocationSlice> payments =
                    bySlab.getOrDefault(slab.getId(), List.of());
            boolean hasReceipts = !payments.isEmpty();

            rows.add(
                    new SlabScheduleLedgerRow(
                            SlabLedgerRowType.SLAB_TOTAL,
                            hasReceipts ? slab.getDueDate() : null,
                            slab.getMilestoneLabel(),
                            due,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));

            BigDecimal runningPaid = ZERO;
            for (ReceiptSlabAllocationSlice payment : payments) {
                runningPaid = runningPaid.add(payment.amount());
                BigDecimal balance = due.subtract(runningPaid).max(ZERO);
                rows.add(
                        new SlabScheduleLedgerRow(
                                SlabLedgerRowType.RECEIPT,
                                payment.paymentDate(),
                                payment.reference(),
                                null,
                                payment.amount(),
                                balance,
                                null,
                                null,
                                null,
                                payment.receiptId()));
            }

            BigDecimal outstanding = due.subtract(runningPaid).max(ZERO);
            if (hasReceipts && outstanding.compareTo(ZERO) > 0) {
                LocalDate interestFrom =
                        slab.getDueDate() != null ? slab.getDueDate() : today;
                int days = (int) Math.max(0, ChronoUnit.DAYS.between(interestFrom, today));
                BigDecimal interest = simpleInterest(outstanding, DEFAULT_ANNUAL_RATE_PERCENT, days);
                String info =
                        IndianRupeesFormatter.formatComma(interest)
                                + " as interest for "
                                + days
                                + " days for "
                                + IndianRupeesFormatter.formatComma(outstanding)
                                + " @ "
                                + DEFAULT_ANNUAL_RATE_PERCENT.stripTrailingZeros().toPlainString()
                                + " %";
                rows.add(
                        new SlabScheduleLedgerRow(
                                SlabLedgerRowType.TODAY,
                                today,
                                "Today",
                                null,
                                outstanding,
                                ZERO,
                                days > 0 ? days : null,
                                interest.compareTo(ZERO) > 0 ? interest : null,
                                info,
                                null));
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public SlabScheduleLedgerSummary summarizeLedger(List<SlabScheduleLedgerRow> rows) {
        BigDecimal totalDue = ZERO;
        BigDecimal totalReceipt = ZERO;
        BigDecimal totalBalance = ZERO;
        BigDecimal totalInterest = ZERO;
        for (SlabScheduleLedgerRow row : rows) {
            if (row.rowType() == SlabLedgerRowType.SLAB_TOTAL && row.amountDue() != null) {
                totalDue = totalDue.add(row.amountDue());
            }
            if (row.rowType() == SlabLedgerRowType.RECEIPT && row.receiptAmount() != null) {
                totalReceipt = totalReceipt.add(row.receiptAmount());
            }
            if (row.rowType() == SlabLedgerRowType.TODAY) {
                if (row.receiptAmount() != null) {
                    totalBalance = totalBalance.add(row.receiptAmount());
                }
                if (row.interest() != null) {
                    totalInterest = totalInterest.add(row.interest());
                }
            }
        }
        return new SlabScheduleLedgerSummary(totalDue, totalReceipt, totalBalance, totalInterest);
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
