package com.floor21.service;

import com.floor21.dto.SlabScheduleLineView;
import com.floor21.dto.SlabScheduleSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.util.IndianRupeesFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemandDraftService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ReceiptPrintService receiptPrintService;
    private final BookingOwnerService bookingOwnerService;

    @Transactional(readOnly = true)
    public byte[] generate(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        List<SlabScheduleLineView> lines = bookingPaymentSlabService.listLineViews(bookingId);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "No payment schedule rows for this booking. Create the slab schedule first.");
        }
        SlabScheduleSummary summary = bookingPaymentSlabService.summarizeLines(bookingId);
        BigDecimal amountDue =
                summary.totalBalanceAmount() != null
                        ? summary.totalBalanceAmount()
                        : BigDecimal.ZERO;

        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocument(doc, booking, lines, summary, amountDue);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate demand draft document", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedFilename(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        String code = booking.getBookingCode() != null ? booking.getBookingCode() : bookingId.toString();
        String safe = code.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Demand_Draft_" + safe + "_" + LocalDate.now() + ".docx";
    }

    private void writeDocument(
            XWPFDocument doc,
            Booking booking,
            List<SlabScheduleLineView> lines,
            SlabScheduleSummary summary,
            BigDecimal amountDue) {
        Builder builder = booking.getBuilder();
        Client client = booking.getClient();
        String ownersLabel = bookingOwnerService.ownersDisplayName(booking);
        Flat flat = booking.getFlat();
        Building building = flat != null ? flat.getBuilding() : null;
        LocalDate today = LocalDate.now();

        addTitle(doc, "DEMAND DRAFT");
        addBlankLine(doc);

        if (builder != null) {
            addParagraph(doc, builder.getCompanyName(), true, 14);
            String builderAddr = joinNonBlank(builder.getAddress(), builder.getCity());
            if (!builderAddr.isBlank()) {
                addParagraph(doc, builderAddr, false, 11);
            }
            if (builder.getPhone() != null && !builder.getPhone().isBlank()) {
                addParagraph(doc, "Phone: " + builder.getPhone(), false, 11);
            }
        }
        addBlankLine(doc);

        addParagraph(doc, "Date: " + DATE_FMT.format(today), false, 11);
        addBlankLine(doc);

        addParagraph(doc, "To,", false, 11);
        addParagraph(doc, ownersLabel, true, 12);
        String clientAddr = clientCorrespondenceAddress(client);
        if (!clientAddr.isBlank()) {
            addParagraph(doc, clientAddr, false, 11);
        }
        String clientPhone = firstNonBlank(client.getMobile1(), client.getMobile2(), client.getPhoneResidence());
        if (!clientPhone.isBlank()) {
            addParagraph(doc, "Phone: " + clientPhone, false, 11);
        }
        addBlankLine(doc);

        String flatNo = flat != null ? nullToDash(flat.getFlatNumber()) : "—";
        String bhk = flat != null && flat.getBhkType() != null ? flat.getBhkType() : "—";
        String buildingName = building != null ? nullToDash(building.getBuildingName()) : "—";
        addParagraph(
                doc,
                "Subject: Demand for payment — Flat "
                        + flatNo
                        + " ("
                        + bhk
                        + "), "
                        + buildingName,
                true,
                12);
        addBlankLine(doc);

        addParagraph(doc, "Dear " + ownersLabel + ",", false, 11);
        addBlankLine(doc);

        addParagraph(
                doc,
                "This is to inform you that, as per the agreed payment schedule for your booking, the following "
                        + "amount is outstanding as on "
                        + DATE_FMT.format(today)
                        + ".",
                false,
                11);
        addBlankLine(doc);

        addLabelValue(doc, "Booking code", nullToDash(booking.getBookingCode()));
        addLabelValue(doc, "Booking date", booking.getBookingDate() != null ? DATE_FMT.format(booking.getBookingDate()) : "—");
        addLabelValue(doc, "Flat / unit", flatNo + " (" + bhk + ")");
        addLabelValue(doc, "Building / project", buildingName);
        if (building != null) {
            String siteAddr = joinNonBlank(building.getAddress(), building.getCity());
            if (!siteAddr.isBlank()) {
                addLabelValue(doc, "Site address", siteAddr);
            }
        }
        BigDecimal consideration = bookingPaymentSlabService.baseConsideration(booking);
        if (consideration != null && consideration.signum() > 0) {
            addLabelValue(doc, "Consideration (base)", IndianRupeesFormatter.formatFigures(consideration));
        }
        addLabelValue(
                doc,
                "Total scheduled (agreed + extra)",
                IndianRupeesFormatter.formatFigures(summary.totalAmount()));
        addLabelValue(
                doc,
                "Total paid to date",
                IndianRupeesFormatter.formatFigures(summary.totalPaidAmount()));
        addBlankLine(doc);

        addParagraph(doc, "Amount due (final outstanding)", true, 12);
        addParagraph(doc, IndianRupeesFormatter.formatFigures(amountDue), true, 16);
        addParagraph(doc, "(" + IndianRupeesFormatter.formatWordsOnly(amountDue) + ")", false, 11);
        addBlankLine(doc);

        addParagraph(doc, "Payment schedule breakdown", true, 12);
        addSlabTable(doc, lines, summary);
        addBlankLine(doc);

        addParagraph(
                doc,
                "You are requested to remit the amount due of "
                        + IndianRupeesFormatter.formatFigures(amountDue)
                        + " ("
                        + IndianRupeesFormatter.formatWordsOnly(amountDue)
                        + ") within fifteen (15) days from the date of this letter. Payments may be made by "
                        + "cheque, demand draft, NEFT/RTGS, or other mode as mutually agreed, quoting your booking "
                        + "code "
                        + nullToDash(booking.getBookingCode())
                        + ".",
                false,
                11);
        addBlankLine(doc);

        addParagraph(
                doc,
                "This demand is issued based on the payment slab schedule recorded in our system. If you have "
                        + "already remitted any amount not yet reflected above, please share payment details for "
                        + "reconciliation.",
                false,
                11);
        addBlankLine(doc);
        addBlankLine(doc);

        addParagraph(
                doc,
                "For " + receiptPrintService.signatoryCompanyForBuilder(builder),
                false,
                11);
        addBlankLine(doc);
        addBlankLine(doc);
        addParagraph(doc, "Authorised signatory", false, 11);
    }

    private static void addSlabTable(
            XWPFDocument doc, List<SlabScheduleLineView> lines, SlabScheduleSummary summary) {
        XWPFTable table = doc.createTable(1, 6);
        setTableFullWidth(table);
        writeHeaderRow(table.getRow(0));

        int n = 1;
        for (SlabScheduleLineView line : lines) {
            BookingPaymentSlab slab = line.slab();
            XWPFTableRow row = table.createRow();
            setCellText(row.getCell(0), String.valueOf(n++));
            setCellText(row.getCell(1), nullToDash(slab.getMilestoneLabel()));
            setCellText(
                    row.getCell(2),
                    slab.getDueDate() != null ? DATE_FMT.format(slab.getDueDate()) : "—");
            setCellText(row.getCell(3), IndianRupeesFormatter.formatFigures(line.dueAmount()));
            setCellText(row.getCell(4), IndianRupeesFormatter.formatFigures(line.paidAmount()));
            setCellText(row.getCell(5), IndianRupeesFormatter.formatFigures(line.balanceAmount()));
        }

        XWPFTableRow totalRow = table.createRow();
        setCellText(totalRow.getCell(0), "");
        setCellText(totalRow.getCell(1), "Total", true);
        setCellText(totalRow.getCell(2), "");
        setCellText(totalRow.getCell(3), IndianRupeesFormatter.formatFigures(summary.totalAmount()), true);
        setCellText(totalRow.getCell(4), IndianRupeesFormatter.formatFigures(summary.totalPaidAmount()), true);
        setCellText(totalRow.getCell(5), IndianRupeesFormatter.formatFigures(summary.totalBalanceAmount()), true);
    }

    private static void writeHeaderRow(XWPFTableRow row) {
        setCellText(row.getCell(0), "#", true);
        setCellText(row.getCell(1), "Milestone", true);
        setCellText(row.getCell(2), "Due date", true);
        setCellText(row.getCell(3), "Schedule amount", true);
        setCellText(row.getCell(4), "Paid", true);
        setCellText(row.getCell(5), "Balance", true);
    }

    private static void setTableFullWidth(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(java.math.BigInteger.valueOf(5000));
    }

    private static void setCellText(XWPFTableCell cell, String text) {
        setCellText(cell, text, false);
    }

    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setFontSize(10);
        run.setBold(bold);
    }

    private static void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(text);
    }

    private static void addParagraph(XWPFDocument doc, String text, boolean bold, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(fontSize);
    }

    private static void addLabelValue(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun labelRun = p.createRun();
        labelRun.setBold(true);
        labelRun.setFontSize(11);
        labelRun.setText(label + ": ");
        XWPFRun valueRun = p.createRun();
        valueRun.setFontSize(11);
        valueRun.setText(value != null ? value : "—");
    }

    private static void addBlankLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static String clientCorrespondenceAddress(Client client) {
        String comm =
                joinNonBlank(
                        client.getCommAddress1(),
                        client.getCommAddress2(),
                        client.getCommAddress3(),
                        client.getCommCity());
        if (!comm.isBlank()) {
            return comm;
        }
        return joinNonBlank(client.getAddress1(), client.getAddress2(), client.getAddress3(), client.getCity());
    }

    private static String joinNonBlank(String... parts) {
        return Stream.of(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String firstNonBlank(String... parts) {
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return "";
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
