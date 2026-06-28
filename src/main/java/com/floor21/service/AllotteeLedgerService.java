package com.floor21.service;

import com.floor21.dto.AllotteeLedgerRow;
import com.floor21.dto.AllotteeLedgerRowType;
import com.floor21.dto.AllotteeLedgerView;
import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Client;
import com.floor21.entity.Receipt;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AllotteeLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ReceiptSlabAllocationService receiptSlabAllocationService;
    private final ReceiptService receiptService;
    private final BookingOwnerService bookingOwnerService;

    @Transactional
    public AllotteeLedgerView buildForBooking(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        bookingPaymentSlabService.prepareSlabMilestones(bookingId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listSlabsForPaymentLedger(bookingId);
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId);
        List<Receipt> receipts = receiptService.listHistoryForBooking(bookingId);
        return buildView(booking, slabs, bySlab, receipts);
    }

    @Transactional(readOnly = true)
    public AllotteeLedgerView buildForBookingReadOnly(UUID bookingId, UUID builderId) {
        Booking booking = bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listSlabsForPaymentLedgerReadOnly(bookingId, builderId);
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                receiptSlabAllocationService.allocateBySlab(bookingId, builderId);
        List<Receipt> receipts = receiptService.listHistoryForBooking(bookingId, builderId);
        return buildView(booking, slabs, bySlab, receipts);
    }

    private AllotteeLedgerView buildView(
            Booking booking,
            List<BookingPaymentSlab> slabs,
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab,
            List<Receipt> receipts) {
        LocalDate openingDate =
                booking.getBookingDate() != null ? booking.getBookingDate() : LocalDate.now();
        BigDecimal agreementValue = bookingPaymentSlabService.baseConsideration(booking);
        if (agreementValue == null) {
            agreementValue = ZERO;
        }
        List<LedgerEvent> events = new ArrayList<>();

        for (Receipt receipt : receipts) {
            if (Boolean.TRUE.equals(receipt.getDishonoured())) {
                continue;
            }
            BigDecimal credit = receiptCredit(receipt);
            if (credit.compareTo(ZERO) <= 0) {
                continue;
            }
            LocalDate payDate =
                    receipt.getReceiptDate() != null ? receipt.getReceiptDate() : openingDate;
            String instalment = paymentInstalmentLabel(receipt, slabs, bySlab);
            events.add(
                    new LedgerEvent(
                            payDate,
                            receipt.getReceiptSerial() != null ? receipt.getReceiptSerial() : 0,
                            credit,
                            paymentTitle(instalment, credit, slabs),
                            paymentDetail(receipt),
                            displayReceiptNumber(receipt),
                            receipt.getReceiptSerial() != null ? receipt.getReceiptSerial() : 0));
        }

        events.sort(
                Comparator.comparing(LedgerEvent::date).thenComparing(LedgerEvent::orderKey));

        LocalDate periodFrom = openingDate;
        LocalDate periodTo = LocalDate.now();
        if (!events.isEmpty()) {
            periodFrom =
                    events.stream()
                            .map(LedgerEvent::date)
                            .min(LocalDate::compareTo)
                            .orElse(openingDate);
            periodTo =
                    events.stream()
                            .map(LedgerEvent::date)
                            .max(LocalDate::compareTo)
                            .orElse(LocalDate.now());
        }
        if (openingDate.isBefore(periodFrom)) {
            periodFrom = openingDate;
        }
        if (LocalDate.now().isAfter(periodTo)) {
            periodTo = LocalDate.now();
        }

        List<AllotteeLedgerRow> rows = new ArrayList<>();
        rows.add(
                new AllotteeLedgerRow(
                        AllotteeLedgerRowType.OPENING,
                        null,
                        openingDate,
                        null,
                        "Opening balance — agreement value",
                        null,
                        null,
                        null,
                        agreementValue,
                        balanceSide(agreementValue)));

        BigDecimal running = agreementValue;
        BigDecimal totalCredit = ZERO;
        int serial = 0;
        for (LedgerEvent event : events) {
            running = running.subtract(event.credit());
            totalCredit = totalCredit.add(event.credit());
            serial++;
            rows.add(
                    new AllotteeLedgerRow(
                            AllotteeLedgerRowType.PAYMENT,
                            serial,
                            event.date(),
                            event.receiptNumber(),
                            event.narrationTitle(),
                            event.narrationDetail(),
                            null,
                            event.credit(),
                            running,
                            balanceSide(running)));
        }

        rows.add(
                new AllotteeLedgerRow(
                        AllotteeLedgerRowType.CLOSING,
                        null,
                        periodTo,
                        null,
                        "Closing balance — " + PERIOD_FMT.format(periodTo),
                        null,
                        null,
                        totalCredit.compareTo(ZERO) > 0 ? totalCredit : null,
                        running,
                        balanceSide(running)));

        BigDecimal gstCollected =
                receipts.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getDishonoured()))
                        .map(r -> r.getAmountGstComponent() != null ? r.getAmountGstComponent() : ZERO)
                        .reduce(ZERO, BigDecimal::add);

        Client primaryOwner =
                bookingOwnerService.ownersInOrder(booking).stream()
                        .findFirst()
                        .orElse(booking.getClient());
        String unit =
                booking.getFlat() != null && booking.getFlat().getFlatNumber() != null
                        ? booking.getFlat().getFlatNumber()
                        : "—";
        String pan =
                primaryOwner != null && primaryOwner.getPanNumber() != null
                        ? primaryOwner.getPanNumber()
                        : null;

        return new AllotteeLedgerView(
                bookingOwnerService.ownersDisplayName(booking),
                pan,
                unit,
                bookingPaymentSlabService.baseConsideration(booking),
                periodFrom,
                periodTo,
                financialYearLabel(periodFrom),
                agreementValue,
                totalCredit,
                running.max(ZERO),
                gstCollected,
                rows);
    }

    private static String paymentInstalmentLabel(
            Receipt receipt,
            List<BookingPaymentSlab> slabs,
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab) {
        for (BookingPaymentSlab slab : slabs) {
            for (ReceiptSlabAllocationSlice slice :
                    bySlab.getOrDefault(slab.getId(), List.of())) {
                if (receipt.getId().equals(slice.receiptId())) {
                    String label = slab.getMilestoneLabel();
                    if (label != null && !label.isBlank()) {
                        return label.trim();
                    }
                }
            }
        }
        return null;
    }

    private static String paymentTitle(
            String instalmentLabel, BigDecimal credit, List<BookingPaymentSlab> slabs) {
        if (instalmentLabel != null && !instalmentLabel.isBlank()) {
            BigDecimal slabDue = ZERO;
            for (BookingPaymentSlab slab : slabs) {
                if (instalmentLabel.equals(slab.getMilestoneLabel())) {
                    slabDue = SlabReceiptWaterfall.slabDue(slab);
                    break;
                }
            }
            if (slabDue.compareTo(ZERO) > 0 && credit.compareTo(slabDue) < 0) {
                return "Payment received — partial";
            }
            return "Payment received — " + instalmentLabel;
        }
        return "Payment received";
    }

    private static String paymentDetail(Receipt receipt) {
        String mode =
                receipt.getPaymentMode() != null && !receipt.getPaymentMode().isBlank()
                        ? receipt.getPaymentMode().trim()
                        : null;
        String ref =
                receipt.getChequeNo() != null && !receipt.getChequeNo().isBlank()
                        ? receipt.getChequeNo().trim()
                        : null;
        String bank =
                receipt.getBankName() != null && !receipt.getBankName().isBlank()
                        ? receipt.getBankName().trim()
                        : null;
        if (mode != null && ref != null && bank != null) {
            return mode + " - " + ref + " · " + bank;
        }
        if (mode != null && ref != null) {
            return mode + " - " + ref;
        }
        if (mode != null && bank != null) {
            return mode + " · " + bank;
        }
        if (ref != null && bank != null) {
            return "Cheque No. " + ref + " · " + bank;
        }
        if (ref != null) {
            return "Ref. " + ref;
        }
        if (mode != null) {
            return mode;
        }
        return "Payment recorded";
    }

    private static BigDecimal receiptCredit(Receipt receipt) {
        BigDecimal consideration =
                receipt.getAmountConsideration() != null
                        ? receipt.getAmountConsideration()
                        : ZERO;
        if (consideration.compareTo(ZERO) > 0) {
            return consideration;
        }
        return receipt.getAmount() != null ? receipt.getAmount() : ZERO;
    }

    private static String displayReceiptNumber(Receipt receipt) {
        if (receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()) {
            return receipt.getReceiptNumber().trim();
        }
        if (receipt.getReceiptSerial() != null) {
            return "RCP-" + receipt.getReceiptSerial();
        }
        return null;
    }

    private static String balanceSide(BigDecimal balance) {
        return balance != null && balance.compareTo(ZERO) > 0 ? "Dr" : "Nil";
    }

    static String financialYearLabel(LocalDate date) {
        if (date == null) {
            return "—";
        }
        int year = date.getYear();
        int startYear = date.getMonthValue() >= Month.APRIL.getValue() ? year : year - 1;
        int endYearShort = (startYear + 1) % 100;
        return startYear + " - " + String.format("%02d", endYearShort);
    }

    private record LedgerEvent(
            LocalDate date,
            int orderKey,
            BigDecimal credit,
            String narrationTitle,
            String narrationDetail,
            String receiptNumber,
            int sortOrder) {}
}
