package com.floor21.service;

import com.floor21.dto.SlabScheduleLineView;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Building;
import com.floor21.entity.Client;
import com.floor21.entity.Receipt;
import com.floor21.repository.ReceiptRepository;
import com.floor21.security.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemandDraftService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ReceiptPrintService receiptPrintService;
    private final BookingOwnerService bookingOwnerService;
    private final BankService bankService;
    private final ReceiptRepository receiptRepository;
    private final DemandLetterTemplateService demandLetterTemplateService;
    private final DemandLetterDocxFiller demandLetterDocxFiller;

    @Transactional(readOnly = true)
    public byte[] generate(UUID bookingId) {
        return generate(bookingId, true, true);
    }

    @Transactional(readOnly = true)
    public byte[] generate(UUID bookingId, boolean includeHeader, boolean includeFooter) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        List<SlabScheduleLineView> lines = bookingPaymentSlabService.listLineViews(bookingId);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "No payment schedule rows for this booking. Create the slab schedule first.");
        }
        DemandLetterModel model = buildModel(lines, receiptTotals(booking.getId()), booking);
        UUID builderId = booking.getBuilder() != null ? booking.getBuilder().getId() : null;
        Building building =
                booking.getFlat() != null ? booking.getFlat().getBuilding() : null;

        try (XWPFDocument doc = demandLetterDocxFiller.openTemplate();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<Client> owners = bookingOwnerService.ownersInOrder(booking);
            ReceiptPrintService.BuilderTaxProfile taxProfile =
                    receiptPrintService.taxProfileForBuilder(booking.getBuilder());
            Bank instalmentBank = builderId != null ? bankService.findActiveInstalmentAccount(builderId) : null;
            Bank gstBank = builderId != null ? bankService.findActiveGstAccount(builderId) : null;
            String branchCity =
                    building != null && building.getCity() != null && !building.getCity().isBlank()
                            ? building.getCity().trim()
                            : booking.getBuilder() != null && booking.getBuilder().getCity() != null
                                    ? booking.getBuilder().getCity().trim()
                                    : "—";
            demandLetterDocxFiller.fill(
                    doc,
                    booking,
                    model,
                    owners,
                    booking.getClient(),
                    taxProfile,
                    receiptPrintService.signatoryCompanyForBuilder(booking.getBuilder()),
                    instalmentBank,
                    gstBank,
                    branchCity);
            if (builderId != null && includeFooter && demandLetterTemplateService.hasFooter(builderId)) {
                demandLetterTemplateService.applyFooterTemplate(doc, builderId);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate demand letter document", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedFilename(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        String code = booking.getBookingCode() != null ? booking.getBookingCode() : bookingId.toString();
        String safe = code.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Demand_Letter_" + safe + "_" + LocalDate.now() + ".docx";
    }

    DemandLetterModel buildModel(
            List<SlabScheduleLineView> lines, ReceiptTotals received, Booking booking) {
        List<DemandPaymentRow> rows = new ArrayList<>();
        int lastIndex = lines.size() - 1;
        BigDecimal uptoInstalment = ZERO;
        BigDecimal uptoTds = ZERO;
        BigDecimal uptoGst = ZERO;

        if (lastIndex > 0) {
            for (int i = 0; i < lastIndex; i++) {
                BigDecimal grossInstalment =
                        lines.get(i).dueAmount() != null ? lines.get(i).dueAmount() : ZERO;
                BigDecimal rowTds = taxOnInstalment(grossInstalment, 1);
                BigDecimal rowGst = taxOnInstalment(grossInstalment, 5);
                uptoInstalment = uptoInstalment.add(netInstalmentAfterTds(grossInstalment, rowTds));
                uptoTds = uptoTds.add(rowTds);
                uptoGst = uptoGst.add(rowGst);
            }
            String uptoLabel =
                    "Upto – "
                            + nullToDash(lines.get(lastIndex - 1).slab().getMilestoneLabel());
            rows.add(new DemandPaymentRow(1, uptoLabel, uptoInstalment, uptoTds, uptoGst, false));
        }

        SlabScheduleLineView currentLine = lines.get(lastIndex);
        BigDecimal currentGross =
                currentLine.dueAmount() != null ? currentLine.dueAmount() : ZERO;
        BigDecimal currentTds = taxOnInstalment(currentGross, 1);
        BigDecimal currentGst = taxOnInstalment(currentGross, 5);
        BigDecimal currentInstalment = netInstalmentAfterTds(currentGross, currentTds);
        String currentLabel = nullToDash(currentLine.slab().getMilestoneLabel());
        rows.add(
                new DemandPaymentRow(
                        rows.size() + 1,
                        currentLabel,
                        currentInstalment,
                        currentTds,
                        currentGst,
                        true));

        BigDecimal totalInstalment =
                rows.stream().map(DemandPaymentRow::instalment).reduce(ZERO, BigDecimal::add);
        BigDecimal totalTds = uptoTds.add(currentTds);
        BigDecimal totalGst = uptoGst.add(currentGst);
        BigDecimal receivedInstalment =
                received.instalment() != null ? received.instalment() : ZERO;
        BigDecimal receivedTds = received.tds() != null ? received.tds() : ZERO;
        BigDecimal receivedGst = received.gst() != null ? received.gst() : ZERO;
        BigDecimal payableInstalment = totalInstalment.subtract(receivedInstalment);
        if (payableInstalment.compareTo(ZERO) < 0) {
            payableInstalment = ZERO;
        }
        BigDecimal payableTds = totalTds.subtract(receivedTds);
        if (payableTds.compareTo(ZERO) < 0) {
            payableTds = ZERO;
        }
        BigDecimal payableGst = totalGst.subtract(receivedGst);
        if (payableGst.compareTo(ZERO) < 0) {
            payableGst = ZERO;
        }
        BigDecimal consideration = baseConsideration(booking);
        return new DemandLetterModel(
                rows,
                lines.get(lastIndex).slab(),
                totalInstalment,
                totalTds,
                totalGst,
                receivedInstalment,
                receivedTds,
                receivedGst,
                payableInstalment,
                payableTds,
                payableGst,
                consideration);
    }

    private BigDecimal baseConsideration(Booking booking) {
        BigDecimal consideration = bookingPaymentSlabService.baseConsideration(booking);
        if (consideration == null) {
            return booking.getConsiderationAmt() != null ? booking.getConsiderationAmt() : ZERO;
        }
        return consideration;
    }

    private ReceiptTotals receiptTotals(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        BigDecimal instalment = ZERO;
        BigDecimal tds = ZERO;
        BigDecimal gst = ZERO;
        for (Receipt receipt :
                receiptRepository.findActiveByBooking_IdOrderByReceiptDateAsc(bookingId, builderId)) {
            instalment = instalment.add(instalmentReceivedAmount(receipt));
            if (receipt.getAmountTds() != null) {
                tds = tds.add(receipt.getAmountTds());
            }
            if (receipt.getAmountGstComponent() != null) {
                gst = gst.add(receipt.getAmountGstComponent());
            }
            if (receipt.getAmountInterestGst() != null) {
                gst = gst.add(receipt.getAmountInterestGst());
            }
        }
        return new ReceiptTotals(instalment, tds, gst);
    }

    /**
     * Amount received toward milestone instalments (excludes GST). Matches payment schedule receipt
     * column — GST is tracked separately in the demand letter GST row.
     */
    static BigDecimal instalmentReceivedAmount(Receipt receipt) {
        BigDecimal fromBreakdown =
                zeroIfNull(receipt.getAmountConsideration())
                        .add(zeroIfNull(receipt.getAmountExtraCharges()))
                        .add(zeroIfNull(receipt.getAmountInterestAgreement()));
        if (fromBreakdown.compareTo(ZERO) > 0) {
            return fromBreakdown;
        }
        BigDecimal total = receipt.getAmount() != null ? receipt.getAmount() : ZERO;
        BigDecimal gst =
                zeroIfNull(receipt.getAmountGstComponent())
                        .add(zeroIfNull(receipt.getAmountInterestGst()));
        return total.subtract(gst).max(ZERO);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private static BigDecimal taxOnInstalment(BigDecimal instalment, int percent) {
        if (instalment == null || instalment.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return instalment
                .multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    /** Instalment payable to builder after buyer deducts TDS. */
    private static BigDecimal netInstalmentAfterTds(BigDecimal grossInstalment, BigDecimal tds) {
        if (grossInstalment == null || grossInstalment.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        BigDecimal deducted = tds != null ? tds : ZERO;
        BigDecimal net = grossInstalment.subtract(deducted);
        return net.compareTo(ZERO) < 0 ? ZERO : net;
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    record DemandPaymentRow(
            int serialNo,
            String scheduleName,
            BigDecimal instalment,
            BigDecimal tds,
            BigDecimal gst,
            boolean currentMilestone) {}

    record DemandLetterModel(
            List<DemandPaymentRow> rows,
            BookingPaymentSlab completedMilestone,
            BigDecimal totalInstalment,
            BigDecimal totalTds,
            BigDecimal totalGst,
            BigDecimal receivedInstalment,
            BigDecimal receivedTds,
            BigDecimal receivedGst,
            BigDecimal payableInstalment,
            BigDecimal payableTds,
            BigDecimal payableGst,
            BigDecimal consideration) {}

    record ReceiptTotals(BigDecimal instalment, BigDecimal tds, BigDecimal gst) {}
}
