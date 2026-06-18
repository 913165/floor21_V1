package com.floor21.service;

import com.floor21.dto.SlabScheduleLineView;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Receipt;
import com.floor21.repository.ReceiptRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.IndianRupeesFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter LETTER_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final String FONT = "Times New Roman";

    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ReceiptPrintService receiptPrintService;
    private final BookingOwnerService bookingOwnerService;
    private final BankService bankService;
    private final ReceiptRepository receiptRepository;

    @Transactional(readOnly = true)
    public byte[] generate(UUID bookingId) {
        Booking booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
        List<SlabScheduleLineView> lines = bookingPaymentSlabService.listLineViews(bookingId);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "No payment schedule rows for this booking. Create the slab schedule first.");
        }
        DemandLetterModel model = buildModel(lines, receiptTotals(booking.getId()));

        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocument(doc, booking, model);
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

    DemandLetterModel buildModel(List<SlabScheduleLineView> lines, ReceiptTotals received) {
        List<DemandPaymentRow> rows = new ArrayList<>();
        BigDecimal totalInstalment = ZERO;
        BigDecimal totalTds = ZERO;
        BigDecimal totalGst = ZERO;
        int serial = 1;
        for (int i = 0; i < lines.size(); i++) {
            SlabScheduleLineView line = lines.get(i);
            BigDecimal instalment = line.dueAmount() != null ? line.dueAmount() : ZERO;
            BigDecimal tds = taxOnInstalment(instalment, 1);
            BigDecimal gst = taxOnInstalment(instalment, 5);
            totalInstalment = totalInstalment.add(instalment);
            totalTds = totalTds.add(tds);
            totalGst = totalGst.add(gst);
            rows.add(
                    new DemandPaymentRow(
                            serial++,
                            nullToDash(line.slab().getMilestoneLabel()),
                            instalment,
                            tds,
                            gst,
                            i == lines.size() - 1));
        }
        return new DemandLetterModel(
                rows,
                lines.get(lines.size() - 1).slab(),
                totalInstalment,
                totalTds,
                totalGst,
                received.instalment(),
                received.tds(),
                received.gst());
    }

    private ReceiptTotals receiptTotals(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        BigDecimal instalment = ZERO;
        BigDecimal tds = ZERO;
        BigDecimal gst = ZERO;
        for (Receipt receipt :
                receiptRepository.findActiveByBooking_IdOrderByReceiptDateAsc(bookingId, builderId)) {
            if (receipt.getAmount() != null) {
                instalment = instalment.add(receipt.getAmount());
            }
            if (receipt.getAmountTds() != null) {
                tds = tds.add(receipt.getAmountTds());
            }
            if (receipt.getAmountGstComponent() != null) {
                gst = gst.add(receipt.getAmountGstComponent());
            }
        }
        return new ReceiptTotals(instalment, tds, gst);
    }

    private static BigDecimal taxOnInstalment(BigDecimal instalment, int percent) {
        if (instalment == null || instalment.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return instalment
                .multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    private void writeDocument(XWPFDocument doc, Booking booking, DemandLetterModel model) {
        Builder builder = booking.getBuilder();
        Client client = booking.getClient();
        Flat flat = booking.getFlat();
        Building building = flat != null ? flat.getBuilding() : null;
        LocalDate letterDate = LocalDate.now();
        LocalDate dueDate = letterDate.plusDays(15);
        List<Client> owners = bookingOwnerService.ownersInOrder(booking);
        ReceiptPrintService.BuilderTaxProfile taxProfile =
                receiptPrintService.taxProfileForBuilder(builder);

        addTitle(doc, "DEMAND LETTER");
        addHeaderTable(
                doc,
                owners,
                client,
                building,
                flat,
                letterDate,
                dueDate,
                taxProfile);
        addBlankLine(doc);

        addParagraph(doc, "SUBJECT: Demand Letter on basis of Work Completion.", true, 11);
        addBlankLine(doc);
        addParagraph(doc, buildReferenceLine(flat, building), true, 11);
        addBlankLine(doc);

        addParagraph(doc, "Sir,", false, 11);
        addBlankLine(doc);
        addAgreementParagraph(doc, booking);
        addBlankLine(doc);
        addParagraph(
                doc,
                "We hereby inform you that the work is completed up to "
                        + nullToDash(model.completedMilestone().getMilestoneLabel())
                        + " and the following amounts are now due.",
                false,
                11);
        addBlankLine(doc);

        addPaymentTable(doc, model);
        addBlankLine(doc);
        addBlankLine(doc);

        addSignatoryBlock(doc, builder);
        addBlankLine(doc);
        addFooterNotes(doc);

        addPageBreak(doc);
        addBankDetailsSection(doc, builder, building);
    }

    private static void addHeaderTable(
            XWPFDocument doc,
            List<Client> owners,
            Client primaryClient,
            Building building,
            Flat flat,
            LocalDate letterDate,
            LocalDate dueDate,
            ReceiptPrintService.BuilderTaxProfile taxProfile) {
        XWPFTable table = doc.createTable(1, 2);
        setTableFullWidth(table);

        XWPFTableCell left = table.getRow(0).getCell(0);
        left.removeParagraph(0);
        addCellLine(left, "To,", false);
        if (owners.isEmpty() && primaryClient != null) {
            addCellLine(left, primaryClient.displayName(), true);
        } else {
            for (Client owner : owners) {
                addCellLine(left, owner.displayName(), true);
            }
        }
        String address = clientCorrespondenceAddress(primaryClient);
        if (!address.isBlank()) {
            addCellLine(left, address, false);
        }
        String phones = ownerPhones(owners, primaryClient);
        if (!phones.isBlank()) {
            addCellLine(left, "Ph- " + phones, false);
        }

        XWPFTableCell right = table.getRow(0).getCell(1);
        right.removeParagraph(0);
        String project = building != null ? nullToDash(building.getBuildingName()) : "—";
        String unitNo = flat != null ? nullToDash(flat.getFlatNumber()) : "—";
        addCellLine(right, "Date: " + LETTER_DATE.format(letterDate), false);
        addCellLine(right, "Due Date: " + LETTER_DATE.format(dueDate), false);
        addCellLine(right, "Project: " + project, false);
        addCellLine(right, "Unit No: " + unitNo, false);
        addCellLine(right, "GSTIN: " + taxProfile.gstin(), false);
        addCellLine(right, "TAN: " + taxProfile.tan(), false);
    }

    private void addAgreementParagraph(XWPFDocument doc, Booking booking) {
        BigDecimal consideration = bookingPaymentSlabService.baseConsideration(booking);
        if (consideration == null) {
            consideration = booking.getConsiderationAmt() != null ? booking.getConsiderationAmt() : ZERO;
        }
        String regPlace =
                booking.getReference() != null && !booking.getReference().isBlank()
                        ? booking.getReference().trim()
                        : "the Sub-Registrar's office";
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        appendRun(p, "As per the Agreement For Sale registered at ", false);
        appendRun(p, regPlace, true);
        appendRun(p, " in your favour, the total agreement value is ", false);
        appendRun(p, IndianRupeesFormatter.formatFigures(consideration), true);
        appendRun(
                p,
                " /- ("
                        + IndianRupeesFormatter.formatWordsOnly(consideration)
                        + ").",
                false);
    }

    private static String buildReferenceLine(Flat flat, Building building) {
        String flatNo = flat != null ? nullToDash(flat.getFlatNumber()) : "—";
        String floor =
                flat != null && flat.getFloorNumber() != null
                        ? ordinalEnglish(flat.getFloorNumber())
                        : "—";
        String project = building != null ? nullToDash(building.getBuildingName()) : "—";
        String site = building != null ? joinNonBlank(building.getAddress(), building.getCity()) : "";
        String atSite = site.isBlank() ? "—" : site;
        return "REFRENCE: Flat No. "
                + flatNo
                + ", "
                + floor
                + " Floor in Proposed Project Name: \""
                + project
                + "\" At "
                + atSite
                + ".";
    }

    private void addPaymentTable(XWPFDocument doc, DemandLetterModel model) {
        XWPFTable table = doc.createTable(1, 5);
        setTableFullWidth(table);
        writePaymentHeader(table.getRow(0));

        for (DemandPaymentRow row : model.rows()) {
            XWPFTableRow tableRow = table.createRow();
            setCellText(tableRow.getCell(0), String.valueOf(row.serialNo()), row.currentMilestone());
            setCellText(tableRow.getCell(1), row.scheduleName(), row.currentMilestone());
            setAmountCell(tableRow.getCell(2), row.instalment(), row.currentMilestone());
            setAmountCell(tableRow.getCell(3), row.tds(), row.currentMilestone());
            setAmountCell(tableRow.getCell(4), row.gst(), row.currentMilestone());
        }

        addSummaryRow(table, "Total Amount", model.totalInstalment(), model.totalTds(), model.totalGst());
        addSummaryRow(
                table,
                "Received Amount",
                model.receivedInstalment(),
                model.receivedTds(),
                model.receivedGst());
        addSummaryRow(
                table,
                "Total Payable",
                model.totalInstalment().subtract(model.receivedInstalment()),
                model.totalTds().subtract(model.receivedTds()),
                model.totalGst().subtract(model.receivedGst()));
    }

    private static void writePaymentHeader(XWPFTableRow row) {
        setCellText(row.getCell(0), "Sr.no.", true);
        setCellText(row.getCell(1), "Schedule Name", true);
        setCellText(row.getCell(2), "Instalment", true);
        setCellText(row.getCell(3), "TDS", true);
        setCellText(row.getCell(4), "GST", true);
    }

    private static void addSummaryRow(
            XWPFTable table,
            String label,
            BigDecimal instalment,
            BigDecimal tds,
            BigDecimal gst) {
        XWPFTableRow row = table.createRow();
        setCellText(row.getCell(0), "", true);
        setCellText(row.getCell(1), label, true);
        setAmountCell(row.getCell(2), instalment, true);
        setAmountCell(row.getCell(3), tds, true);
        setAmountCell(row.getCell(4), gst, true);
    }

    private void addSignatoryBlock(XWPFDocument doc, Builder builder) {
        addParagraph(
                doc,
                "For " + receiptPrintService.signatoryCompanyForBuilder(builder),
                false,
                11);
        addBlankLine(doc);
        addBlankLine(doc);
        addParagraph(doc, "Authorized Signatory", false, 11);
    }

    private static void addFooterNotes(XWPFDocument doc) {
        addBullet(
                doc,
                "TDS @ 1% to be deducted by the buyer and paid to the Government under Section 194-IA "
                        + "of the Income Tax Act, 1961 for property value exceeding Rs. 50 Lac.");
        addBullet(doc, "GST @ 5% is payable on demand as per the agreement.");
        addBullet(doc, "Delay in payment attracts interest @ 18% per annum.");
        addBullet(doc, "Please ignore if already paid.");
    }

    private void addBankDetailsSection(XWPFDocument doc, Builder builder, Building building) {
        UUID builderId = builder != null ? builder.getId() : null;
        Bank instalmentBank = builderId != null ? bankService.findActiveInstalmentAccount(builderId) : null;
        Bank gstBank = builderId != null ? bankService.findActiveGstAccount(builderId) : null;
        String branchCity =
                building != null && building.getCity() != null && !building.getCity().isBlank()
                        ? building.getCity().trim()
                        : builder != null && builder.getCity() != null ? builder.getCity().trim() : "—";

        addParagraph(doc, "For online payment bank details are as below", true, 11, true);
        addBlankLine(doc);

        XWPFTable table = doc.createTable(1, 3);
        setTableFullWidth(table);
        setCellText(table.getRow(0).getCell(0), "", true);
        setCellText(table.getRow(0).getCell(1), "For Flat cost instalment", true);
        setCellText(table.getRow(0).getCell(2), "For GST", true);

        addBankDetailRow(table, "Bank account Number", instalmentBank, gstBank, Bank::getAccountNumber);
        addBankDetailRow(table, "Name of Account Holder", instalmentBank, gstBank, Bank::getAccountHolderName);
        addBankDetailRow(table, "Account Type", instalmentBank, gstBank, b -> "Current");
        addBankDetailRow(table, "Name of Bank", instalmentBank, gstBank, Bank::getBankName);
        addBankDetailRow(table, "Branch Name", instalmentBank, gstBank, Bank::getBranch);
        addBankDetailRow(table, "Branch City", instalmentBank, gstBank, b -> branchCity);
        addBankDetailRow(table, "IFSC", instalmentBank, gstBank, Bank::getIfscCode);
    }

    private static void addBankDetailRow(
            XWPFTable table,
            String label,
            Bank instalmentBank,
            Bank gstBank,
            java.util.function.Function<Bank, String> extractor) {
        XWPFTableRow row = table.createRow();
        setCellText(row.getCell(0), label, true);
        setCellText(row.getCell(1), bankField(instalmentBank, extractor), false);
        setCellText(row.getCell(2), bankField(gstBank, extractor), false);
    }

    private static String bankField(Bank bank, java.util.function.Function<Bank, String> extractor) {
        if (bank == null) {
            return "—";
        }
        String value = extractor.apply(bank);
        return value != null && !value.isBlank() ? value.trim() : "—";
    }

    private static void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setFontFamily(FONT);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText(text);
        addBlankLine(doc);
    }

    private static void addParagraph(XWPFDocument doc, String text, boolean bold, int fontSize) {
        addParagraph(doc, text, bold, fontSize, false);
    }

    private static void addParagraph(
            XWPFDocument doc, String text, boolean bold, int fontSize, boolean underline) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily(FONT);
        if (underline) {
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
    }

    private static void addBullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(360);
        XWPFRun run = p.createRun();
        run.setText("• " + text);
        run.setFontSize(10);
        run.setFontFamily(FONT);
    }

    private static void appendRun(XWPFParagraph paragraph, String text, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(11);
        run.setFontFamily(FONT);
    }

    private static void addCellLine(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(10);
        run.setFontFamily(FONT);
    }

    private static void setTableFullWidth(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(java.math.BigInteger.valueOf(5000));
    }

    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setFontSize(10);
        run.setFontFamily(FONT);
        run.setBold(bold);
    }

    private static void setAmountCell(XWPFTableCell cell, BigDecimal amount, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun run = p.createRun();
        run.setText(formatTableAmount(amount));
        run.setFontSize(10);
        run.setFontFamily(FONT);
        run.setBold(bold);
    }

    private static String formatTableAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return IndianRupeesFormatter.formatComma(amount.setScale(0, RoundingMode.HALF_UP));
    }

    private static void addBlankLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static void addPageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.addBreak(BreakType.PAGE);
    }

    private static String clientCorrespondenceAddress(Client client) {
        if (client == null) {
            return "";
        }
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

    private static String ownerPhones(List<Client> owners, Client fallback) {
        List<Client> list = owners.isEmpty() && fallback != null ? List.of(fallback) : owners;
        return list.stream()
                .map(
                        c ->
                                firstNonBlank(
                                        c.getMobile1(), c.getMobile2(), c.getPhoneResidence()))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" , "));
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

    private static String ordinalEnglish(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
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
            BigDecimal receivedGst) {}

    record ReceiptTotals(BigDecimal instalment, BigDecimal tds, BigDecimal gst) {}
}
