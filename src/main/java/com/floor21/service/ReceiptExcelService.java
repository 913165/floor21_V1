package com.floor21.service;

import com.floor21.dto.ReceiptImportRow;
import com.floor21.entity.Booking;
import com.floor21.entity.Client;
import com.floor21.entity.Receipt;
import com.floor21.util.PoiSheetSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ReceiptExcelService {

    private static final int MAX_ROWS = 500;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String[] TEMPLATE_HEADERS = {
        "Sno",
        "Receipt Date",
        "Rec. No.",
        "Consideration",
        "Extra Charges",
        "Interest (Agreement)",
        "Interest (GST)",
        "TDS",
        "GST",
        "Payment Mode",
        "Cheque/Ref No.",
        "Cheque Date",
        "Bank",
        "Paid By",
        "Entered By",
        "Remarks",
        "Dishonoured"
    };

    private final ReceiptService receiptService;
    private final BookingOwnerService bookingOwnerService;

    public byte[] buildImportTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payment receipts");
            Row header = sheet.createRow(0);
            for (int c = 0; c < TEMPLATE_HEADERS.length; c++) {
                header.createCell(c).setCellValue(TEMPLATE_HEADERS[c]);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue(1);
            sample.createCell(1).setCellValue("2026-04-26");
            sample.createCell(2).setCellValue("1");
            sample.createCell(3).setCellValue(400000);
            sample.createCell(4).setCellValue(0);
            sample.createCell(5).setCellValue(0);
            sample.createCell(6).setCellValue(0);
            sample.createCell(7).setCellValue(0);
            sample.createCell(8).setCellValue(0);
            sample.createCell(9).setCellValue("Cheque");
            sample.createCell(10).setCellValue("123456");
            sample.createCell(11).setCellValue("2026-04-26");
            sample.createCell(12).setCellValue("HDFC Bank");
            sample.createCell(13).setCellValue("Maneesha Gupta");
            sample.createCell(14).setCellValue("Pankaj Gupta");
            sample.createCell(15).setCellValue("");
            sample.createCell(16).setCellValue("No");
            PoiSheetSupport.autoSizeColumns(sheet, TEMPLATE_HEADERS.length);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public int importForBooking(UUID bookingId, MultipartFile file) {
        validateFile(file);
        List<ReceiptImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = parse(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No receipt rows found. Use the sample Excel template (row 1 = headers).");
        }
        Booking booking = receiptService.requireBookingForImport(bookingId);
        for (ReceiptImportRow row : rows) {
            Receipt form = toReceiptForm(row, booking);
            receiptService.saveImported(bookingId, form, row.receiptNumber(), row.enteredBy());
        }
        return rows.size();
    }

    private Receipt toReceiptForm(ReceiptImportRow row, Booking booking) {
        Receipt form = new Receipt();
        form.setReceiptDate(row.receiptDate());
        form.setChequeDate(row.chequeDate());
        form.setAmountConsideration(zeroIfNull(row.consideration()));
        form.setAmountExtraCharges(zeroIfNull(row.extraCharges()));
        form.setAmountInterestAgreement(zeroIfNull(row.interestAgreement()));
        form.setAmountInterestGst(zeroIfNull(row.interestGst()));
        form.setAmountTds(zeroIfNull(row.tds()));
        form.setAmountGstComponent(zeroIfNull(row.gstComponent()));
        form.setPaymentMode(emptyToNull(row.paymentMode()));
        form.setChequeNo(emptyToNull(row.chequeNo()));
        form.setBankName(emptyToNull(row.bankName()));
        form.setRemarks(emptyToNull(row.remarks()));
        form.setDishonoured(row.dishonoured());
        UUID paidById = resolvePaidByClientId(booking, row.paidBy(), row.excelRow());
        if (paidById != null) {
            Client payer = new Client();
            payer.setId(paidById);
            form.setPaidByClient(payer);
        }
        return form;
    }

    private UUID resolvePaidByClientId(Booking booking, String paidByName, int excelRow) {
        if (paidByName == null || paidByName.isBlank()) {
            return null;
        }
        String needle = paidByName.trim().toLowerCase(Locale.ROOT);
        for (Client owner : bookingOwnerService.ownersInOrder(booking)) {
            if (owner == null) {
                continue;
            }
            String display = owner.displayName();
            if (display != null && display.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return owner.getId();
            }
        }
        throw new IllegalArgumentException(
                "Row "
                        + excelRow
                        + ": Paid by '"
                        + paidByName.trim()
                        + "' must match a booking owner name exactly.");
    }

    List<ReceiptImportRow> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, formatter, evaluator);
            Row header = sheet.getRow(headerRowIndex);
            int receiptDateCol = findColumn(header, formatter, evaluator, "receipt_date", 1);
            int receiptNoCol = findColumn(header, formatter, evaluator, "receipt_no", 2);
            int considerationCol = findColumn(header, formatter, evaluator, "consideration", 3);
            int extraCol = findColumn(header, formatter, evaluator, "extra", 4);
            int intAgreeCol = findColumn(header, formatter, evaluator, "interest_agreement", 5);
            int intGstCol = findColumn(header, formatter, evaluator, "interest_gst", 6);
            int tdsCol = findColumn(header, formatter, evaluator, "tds", 7);
            int gstCol = findColumn(header, formatter, evaluator, "gst", 8);
            int modeCol = findColumn(header, formatter, evaluator, "payment_mode", 9);
            int chequeNoCol = findColumn(header, formatter, evaluator, "cheque_no", 10);
            int chequeDateCol = findColumn(header, formatter, evaluator, "cheque_date", 11);
            int bankCol = findColumn(header, formatter, evaluator, "bank", 12);
            int paidByCol = findColumn(header, formatter, evaluator, "paid_by", 13);
            int enteredByCol = findColumn(header, formatter, evaluator, "entered_by", 14);
            int remarksCol = findColumn(header, formatter, evaluator, "remarks", 15);
            int dishonouredCol = findColumn(header, formatter, evaluator, "dishonoured", 16);

            List<ReceiptImportRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum() && rows.size() < MAX_ROWS; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (!rowHasReceiptData(
                        row,
                        formatter,
                        evaluator,
                        receiptDateCol,
                        considerationCol,
                        extraCol,
                        intAgreeCol,
                        intGstCol,
                        tdsCol,
                        gstCol)) {
                    continue;
                }
                int excelRow = r + 1;
                LocalDate receiptDate =
                        parseDate(
                                row.getCell(receiptDateCol),
                                formatter,
                                evaluator,
                                excelRow,
                                "Receipt Date",
                                true);
                LocalDate chequeDate =
                        parseDate(
                                row.getCell(chequeDateCol),
                                formatter,
                                evaluator,
                                excelRow,
                                "Cheque Date",
                                false);
                rows.add(
                        new ReceiptImportRow(
                                excelRow,
                                receiptDate,
                                emptyToNull(cellText(row.getCell(receiptNoCol), formatter, evaluator)),
                                parseAmount(
                                        row.getCell(considerationCol), formatter, evaluator, excelRow, "Consideration"),
                                parseAmount(
                                        row.getCell(extraCol), formatter, evaluator, excelRow, "Extra Charges"),
                                parseAmount(
                                        row.getCell(intAgreeCol),
                                        formatter,
                                        evaluator,
                                        excelRow,
                                        "Interest (Agreement)"),
                                parseAmount(
                                        row.getCell(intGstCol), formatter, evaluator, excelRow, "Interest (GST)"),
                                parseAmount(row.getCell(tdsCol), formatter, evaluator, excelRow, "TDS"),
                                parseAmount(row.getCell(gstCol), formatter, evaluator, excelRow, "GST"),
                                cellText(row.getCell(modeCol), formatter, evaluator),
                                cellText(row.getCell(chequeNoCol), formatter, evaluator),
                                chequeDate,
                                cellText(row.getCell(bankCol), formatter, evaluator),
                                cellText(row.getCell(paidByCol), formatter, evaluator),
                                cellText(row.getCell(enteredByCol), formatter, evaluator),
                                cellText(row.getCell(remarksCol), formatter, evaluator),
                                parseDishonoured(row.getCell(dishonouredCol), formatter, evaluator)));
            }
            return rows;
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an Excel file (.xlsx or .xls) to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File is too large (max 5 MB).");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("Only Excel files (.xlsx or .xls) are supported.");
        }
    }

    private static boolean rowHasReceiptData(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int receiptDateCol,
            int... amountCols) {
        if (!cellText(row.getCell(receiptDateCol), formatter, evaluator).isBlank()) {
            return true;
        }
        for (int col : amountCols) {
            String raw = cellText(row.getCell(col), formatter, evaluator);
            if (!raw.isBlank() && !"0".equals(raw) && !"0.0".equals(raw) && !"0.00".equals(raw)) {
                return true;
            }
        }
        return false;
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), 10);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean hasReceiptDate = false;
            boolean hasConsideration = false;
            for (Cell cell : row) {
                String v = cellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
                if (v.contains("receipt") && v.contains("date")) {
                    hasReceiptDate = true;
                }
                if (v.contains("consideration")) {
                    hasConsideration = true;
                }
            }
            if (hasReceiptDate && hasConsideration) {
                return r;
            }
        }
        return 0;
    }

    private static int findColumn(
            Row header, DataFormatter formatter, FormulaEvaluator evaluator, String keyword, int fallback) {
        if (header == null) {
            return fallback;
        }
        for (Cell cell : header) {
            String v = cellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
            switch (keyword) {
                case "receipt_date" -> {
                    if (v.contains("receipt") && v.contains("date")) {
                        return cell.getColumnIndex();
                    }
                }
                case "receipt_no" -> {
                    if (v.contains("rec") && v.contains("no")) {
                        return cell.getColumnIndex();
                    }
                }
                case "consideration" -> {
                    if (v.contains("consideration")) {
                        return cell.getColumnIndex();
                    }
                }
                case "extra" -> {
                    if (v.contains("extra")) {
                        return cell.getColumnIndex();
                    }
                }
                case "interest_agreement" -> {
                    if (v.contains("interest") && v.contains("agreement")) {
                        return cell.getColumnIndex();
                    }
                }
                case "interest_gst" -> {
                    if (v.contains("interest") && v.contains("gst")) {
                        return cell.getColumnIndex();
                    }
                }
                case "tds" -> {
                    if (v.equals("tds")) {
                        return cell.getColumnIndex();
                    }
                }
                case "gst" -> {
                    if (v.equals("gst")) {
                        return cell.getColumnIndex();
                    }
                }
                case "payment_mode" -> {
                    if (v.contains("payment") && v.contains("mode")) {
                        return cell.getColumnIndex();
                    }
                }
                case "cheque_no" -> {
                    if ((v.contains("cheque") || v.contains("ref")) && !v.contains("date")) {
                        return cell.getColumnIndex();
                    }
                }
                case "cheque_date" -> {
                    if (v.contains("cheque") && v.contains("date")) {
                        return cell.getColumnIndex();
                    }
                }
                case "bank" -> {
                    if (v.equals("bank") || v.contains("bank name")) {
                        return cell.getColumnIndex();
                    }
                }
                case "paid_by" -> {
                    if (v.contains("paid") && v.contains("by")) {
                        return cell.getColumnIndex();
                    }
                }
                case "entered_by" -> {
                    if (v.contains("entered") && v.contains("by")) {
                        return cell.getColumnIndex();
                    }
                }
                case "remarks" -> {
                    if (v.contains("remark") || v.contains("particular")) {
                        return cell.getColumnIndex();
                    }
                }
                case "dishonoured" -> {
                    if (v.contains("dishonour") || v.contains("dishonor")) {
                        return cell.getColumnIndex();
                    }
                }
                default -> {}
            }
        }
        return fallback;
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private static LocalDate parseDate(
            Cell cell,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRow,
            String label,
            boolean required) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            if (required) {
                throw new IllegalArgumentException("Row " + excelRow + ": " + label + " is required.");
            }
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = cellText(cell, formatter, evaluator);
        if (text.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Row " + excelRow + ": " + label + " is required.");
            }
            return null;
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter pattern :
                List.of(
                        DateTimeFormatter.ofPattern("d/M/uuuu"),
                        DateTimeFormatter.ofPattern("d-M-uuuu"),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                        DateTimeFormatter.ofPattern("dd-MM-uuuu"))) {
            try {
                return LocalDate.parse(text, pattern);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException(
                "Row " + excelRow + ": " + label + " must be a date (e.g. 2026-04-26 or 26/04/2026).");
    }

    private static BigDecimal parseAmount(
            Cell cell, DataFormatter formatter, FormulaEvaluator evaluator, int excelRow, String label) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return BigDecimal.ZERO;
        }
        String text = cellText(cell, formatter, evaluator);
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            String normalized = text.replace(",", "");
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Row " + excelRow + ": " + label + " must be a number.");
        }
    }

    private static boolean parseDishonoured(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        String text = cellText(cell, formatter, evaluator);
        if (text.isBlank()) {
            return false;
        }
        String v = text.toLowerCase(Locale.ROOT);
        return v.equals("y")
                || v.equals("yes")
                || v.equals("true")
                || v.equals("1")
                || v.equals("dishonoured")
                || v.equals("dishonored");
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
