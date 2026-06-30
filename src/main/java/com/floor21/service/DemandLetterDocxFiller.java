package com.floor21.service;

import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Builder;
import com.floor21.util.DocxTokenReplacer;
import com.floor21.util.IndianRupeesFormatter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/** Fills the bundled Word template to match the partner demand-letter layout. */
@Component
class DemandLetterDocxFiller {

    private static final String TEMPLATE_PATH = "/demand-letter/default-demand-letter.docx";
    private static final DateTimeFormatter LETTER_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

  // Sample literals from docs/ademnpw.docx used as replace keys (body paragraphs only).
    private static final String SAMPLE_PROJECT = "LA VESTA";
    private static final String SAMPLE_FLAT = "1605";
    private static final String SAMPLE_FLOOR = "16th Floor";
    private static final String SAMPLE_SITE = "Plot No.17+31+32, Sector-13, Nerul, Navi Mumbai";
    private static final String SAMPLE_REG_PLACE = "CBD-Belapur";
    private static final String SAMPLE_CONSIDERATION_FIGURES = "2,11,93,000";
    private static final String SAMPLE_CONSIDERATION_WORDS =
            "Rupees Two Crore Eleven Lakh Ninety Three Thousand Only";
    private static final String SAMPLE_MILESTONE = "On or before completion 4th Slab";
    private static final String SAMPLE_SIGNATORY = "SEAVISTA INFRASTRUCTURE LLP";

    XWPFDocument openTemplate() throws IOException {
        InputStream in = DemandLetterDocxFiller.class.getResourceAsStream(TEMPLATE_PATH);
        if (in == null) {
            throw new IllegalStateException("Demand letter template not found: " + TEMPLATE_PATH);
        }
        try (InputStream stream = in) {
            return new XWPFDocument(stream);
        }
    }

    void fill(
            XWPFDocument doc,
            Booking booking,
            DemandDraftService.DemandLetterModel model,
            List<Client> owners,
            Client primaryClient,
            ReceiptPrintService.BuilderTaxProfile taxProfile,
            String signatoryCompany,
            Bank instalmentBank,
            Bank gstBank,
            String branchCity) {
        Flat flat = booking.getFlat();
        Building building = flat != null ? flat.getBuilding() : null;
        LocalDate letterDate = LocalDate.now();
        LocalDate dueDate = letterDate.plusDays(15);

        BigDecimal consideration = model.consideration() != null ? model.consideration() : BigDecimal.ZERO;
        String regPlace =
                booking.getReference() != null && !booking.getReference().isBlank()
                        ? booking.getReference().trim()
                        : "CBD-Belapur";
        String agreementDate =
                booking.getBookingDate() != null
                        ? LETTER_DATE.format(booking.getBookingDate())
                        : "";

        Map<String, String> tokens = new HashMap<>();
        tokens.put(SAMPLE_REG_PLACE, regPlace);
        tokens.put(SAMPLE_CONSIDERATION_FIGURES, IndianRupeesFormatter.formatFigures(consideration));
        tokens.put(SAMPLE_CONSIDERATION_WORDS, IndianRupeesFormatter.formatWordsOnly(consideration));
        tokens.put(SAMPLE_MILESTONE, nullToDash(model.completedMilestone().getMilestoneLabel()));
        tokens.put(SAMPLE_SIGNATORY, signatoryCompany);
        tokens.put(SAMPLE_FLAT, flat != null ? nullToDash(flat.getFlatNumber()) : "—");
        tokens.put(
                SAMPLE_FLOOR,
                flat != null && flat.getFloorNumber() != null
                        ? ordinalEnglish(flat.getFloorNumber()) + " Floor"
                        : "—");
        tokens.put(SAMPLE_SITE, siteLine(building));
        tokens.put(SAMPLE_PROJECT, building != null ? nullToDash(building.getBuildingName()) : "—");
        if (!agreementDate.isBlank()) {
            tokens.put("vide agreement dated ,", "vide agreement dated " + agreementDate + ",");
        }

        putPaymentRowTokens(tokens, model);
        putBankTokens(tokens, instalmentBank, gstBank, branchCity);

        DocxTokenReplacer.replaceAll(doc, tokens);

        XWPFTable headerTable = findTableContaining(doc, "To,");
        if (headerTable != null) {
            fillHeaderTable(
                    headerTable,
                    owners,
                    primaryClient,
                    building,
                    flat,
                    letterDate,
                    dueDate,
                    taxProfile);
        }
        XWPFTable paymentTable = findTableContaining(doc, "Sr.no.");
        if (paymentTable != null) {
            adjustPaymentDataRows(paymentTable, model);
            updatePaymentSummaryRows(paymentTable, model);
        }
        XWPFTable bankTable = findTableContaining(doc, "For Flat cost instalment");
        if (bankTable != null && bankTable.getNumberOfRows() > 2) {
            setCellText(
                    bankTable.getRow(2).getCell(1),
                    bankField(instalmentBank, Bank::getAccountHolderName));
        }
    }

    private static void putPaymentRowTokens(
            Map<String, String> tokens, DemandDraftService.DemandLetterModel model) {
        List<DemandDraftService.DemandPaymentRow> rows = model.rows();
        if (rows.isEmpty()) {
            return;
        }
        if (rows.size() >= 2) {
            DemandDraftService.DemandPaymentRow upto = rows.get(0);
            DemandDraftService.DemandPaymentRow current = rows.get(1);
            tokens.put("9966008", formatTableAmount(upto.instalment()));
            tokens.put("1,00,667", formatTableAmount(upto.tds()));
            tokens.put("5,03,334", formatTableAmount(upto.gst()));
            tokens.put("Upto – On or before completion 2nd Slab", upto.scheduleName());
            tokens.put("524527", formatTableAmount(current.instalment()));
            tokens.put("5,298", formatTableAmount(current.tds()));
            tokens.put("26,491", formatTableAmount(current.gst()));
            tokens.put(SAMPLE_MILESTONE, current.scheduleName());
        }
    }

    private static void adjustPaymentDataRows(
            XWPFTable paymentTable, DemandDraftService.DemandLetterModel model) {
        if (model.rows().size() == 1) {
            int totalRowIndex = findRowIndex(paymentTable, "Total Amount");
            if (totalRowIndex > 1) {
                paymentTable.removeRow(1);
            }
            DemandDraftService.DemandPaymentRow row = model.rows().get(0);
            setDataRow(paymentTable, 1, 1, row.scheduleName(), row.instalment(), row.tds(), row.gst());
            return;
        }
        if (model.rows().size() >= 2) {
            DemandDraftService.DemandPaymentRow upto = model.rows().get(0);
            DemandDraftService.DemandPaymentRow current = model.rows().get(1);
            setDataRow(paymentTable, 1, 1, upto.scheduleName(), upto.instalment(), upto.tds(), upto.gst());
            setDataRow(
                    paymentTable,
                    2,
                    2,
                    current.scheduleName(),
                    current.instalment(),
                    current.tds(),
                    current.gst());
        }
    }

    private static void setDataRow(
            XWPFTable table,
            int rowIndex,
            int serialNo,
            String scheduleName,
            BigDecimal instalment,
            BigDecimal tds,
            BigDecimal gst) {
        if (rowIndex >= table.getNumberOfRows()) {
            return;
        }
        XWPFTableRow row = table.getRow(rowIndex);
        setCellText(row.getCell(0), String.valueOf(serialNo));
        setCellText(row.getCell(1), scheduleName);
        setCellText(row.getCell(2), formatTableAmount(instalment));
        setCellText(row.getCell(3), formatTableAmount(tds));
        setCellText(row.getCell(4), formatTableAmount(gst));
    }

    private static void updatePaymentSummaryRows(
            XWPFTable table, DemandDraftService.DemandLetterModel model) {
        setSummaryRow(
                table,
                "Total Amount",
                model.totalInstalment(),
                model.totalTds(),
                model.totalGst());
        setSummaryRow(
                table,
                "Received Amount",
                model.receivedInstalment(),
                model.receivedTds(),
                model.receivedGst());
        setSummaryRow(
                table,
                "Total Payable",
                model.payableInstalment(),
                model.payableTds(),
                model.payableGst());
    }

    private static void setSummaryRow(
            XWPFTable table, String label, BigDecimal instalment, BigDecimal tds, BigDecimal gst) {
        int rowIndex = findRowIndex(table, label);
        if (rowIndex < 0) {
            return;
        }
        XWPFTableRow row = table.getRow(rowIndex);
        setCellText(row.getCell(2), formatTableAmount(instalment));
        setCellText(row.getCell(3), formatTableAmount(tds));
        setCellText(row.getCell(4), formatTableAmount(gst));
    }

    private static void fillHeaderTable(
            XWPFTable table,
            List<Client> owners,
            Client primaryClient,
            Building building,
            Flat flat,
            LocalDate letterDate,
            LocalDate dueDate,
            ReceiptPrintService.BuilderTaxProfile taxProfile) {
        if (table.getNumberOfRows() < 7) {
            return;
        }
        setCellLines(table.getRow(0).getCell(0), toBlockLines(owners, primaryClient));
        setCellText(table.getRow(0).getCell(1), "Date: " + LETTER_DATE.format(letterDate));
        setCellText(table.getRow(1).getCell(1), "Due Date: " + LETTER_DATE.format(dueDate));
        setCellLines(table.getRow(2).getCell(0), addressLines(primaryClient));
        setCellText(
                table.getRow(2).getCell(1),
                "Project : " + (building != null ? nullToDash(building.getBuildingName()) : "—"));
        setCellText(
                table.getRow(3).getCell(1),
                "Unit No: " + (flat != null ? nullToDash(flat.getFlatNumber()) : "—"));
        setCellText(table.getRow(4).getCell(1), "GSTIN: " + taxProfile.gstin());
        setCellText(table.getRow(5).getCell(1), "TAN:" + taxProfile.tan());
        String phones = ownerPhones(owners, primaryClient);
        setCellText(table.getRow(6).getCell(0), phones.isBlank() ? "" : "Ph- " + phones);
    }

    private static List<String> toBlockLines(List<Client> owners, Client primaryClient) {
        List<Client> list = new ArrayList<>();
        if (owners.isEmpty() && primaryClient != null) {
            list.add(primaryClient);
        } else {
            list.addAll(owners);
        }
        List<String> lines = new ArrayList<>();
        lines.add("To,");
        for (Client owner : list) {
            lines.add(owner.displayName());
        }
        return lines;
    }

    private static List<String> addressLines(Client client) {
        if (client == null) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        addLineIfPresent(lines, client.getCommAddress1());
        addLineIfPresent(lines, client.getCommAddress2());
        addLineIfPresent(lines, client.getCommAddress3());
        if (!lines.isEmpty()) {
            addLineIfPresent(lines, client.getCommCity());
            if (!lines.isEmpty()) {
                return lines;
            }
        }
        addLineIfPresent(lines, client.getAddress1());
        addLineIfPresent(lines, client.getAddress2());
        addLineIfPresent(lines, client.getAddress3());
        addLineIfPresent(lines, client.getCity());
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static void addLineIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value.trim());
        }
    }

    private static void setCellLines(XWPFTableCell cell, List<String> lines) {
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
        for (String line : lines) {
            cell.addParagraph().createRun().setText(line != null ? line : "");
        }
    }

    private static void setCellText(XWPFTableCell cell, String text) {
        setCellLines(cell, List.of(text != null ? text : ""));
    }

    private static void putBankTokens(
            Map<String, String> tokens,
            Bank instalmentBank,
            Bank gstBank,
            String branchCity) {
        tokens.put("10210819652", bankField(instalmentBank, Bank::getAccountNumber));
        tokens.put("409002306453", bankField(gstBank, Bank::getAccountNumber));
        tokens.put("SEAVISTA INFRSTRUCTURE LLP", bankField(gstBank, Bank::getAccountHolderName));
        tokens.put("IDFC FIRST Bank", bankField(instalmentBank, Bank::getBankName));
        tokens.put("RBL Bank Ltd", bankField(gstBank, Bank::getBankName));
        tokens.put("Kharghar", bankField(instalmentBank, Bank::getBranch));
        tokens.put("Mumbai", branchCity != null && !branchCity.isBlank() ? branchCity : "—");
        tokens.put("IDFB0040134", bankField(instalmentBank, Bank::getIfscCode));
        tokens.put("RATN0000078", bankField(gstBank, Bank::getIfscCode));
    }

    private static XWPFTable findTableContaining(XWPFDocument doc, String needle) {
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    if (cell.getText() != null && cell.getText().contains(needle)) {
                        return table;
                    }
                }
            }
        }
        return null;
    }

    private static int findRowIndex(XWPFTable table, String needle) {
        List<XWPFTableRow> rows = table.getRows();
        for (int i = 0; i < rows.size(); i++) {
            for (XWPFTableCell cell : rows.get(i).getTableCells()) {
                if (cell.getText() != null && cell.getText().contains(needle)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String ownerPhones(List<Client> owners, Client fallback) {
        List<Client> list = owners.isEmpty() && fallback != null ? List.of(fallback) : owners;
        return list.stream()
                .map(c -> firstNonBlank(c.getMobile1(), c.getMobile2(), c.getPhoneResidence()))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" , "));
    }

    private static String siteLine(Building building) {
        if (building == null) {
            return "—";
        }
        String site = joinNonBlank(building.getAddress(), building.getCity());
        return site.isBlank() ? "—" : site;
    }

    private static String bankField(Bank bank, java.util.function.Function<Bank, String> extractor) {
        if (bank == null) {
            return "—";
        }
        String value = extractor.apply(bank);
        return value != null && !value.isBlank() ? value.trim() : "—";
    }

    private static String formatTableAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return IndianRupeesFormatter.formatComma(amount.setScale(0, RoundingMode.HALF_UP));
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
}
