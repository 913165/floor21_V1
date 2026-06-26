package com.floor21.service;

import com.floor21.dto.ClientImportRow;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.PoiSheetSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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
public class ClientExcelService {

    private static final int MAX_ROWS = 500;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String[] TEMPLATE_HEADERS = {
        "Sno",
        "First Name",
        "Last Name",
        "Company",
        "Occupation",
        "Address Line 1",
        "Address Line 2",
        "City",
        "Phone 1",
        "Phone 2",
        "Email",
        "PAN No",
        "Aadhaar",
        "Date of Birth",
        "Date of Marriage",
        "Particulars"
    };

    private final ClientRepository clientRepository;
    private final BuilderRepository builderRepository;

    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] exportClients(List<Client> clients, boolean includeProjectColumn) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Clients");
            Row header = sheet.createRow(0);
            int headerCol = 0;
            for (String label : exportHeaderLabels(includeProjectColumn)) {
                header.createCell(headerCol++).setCellValue(label);
            }
            int rowIndex = 1;
            for (Client client : clients) {
                Row row = sheet.createRow(rowIndex);
                writeExportRow(row, rowIndex, client, includeProjectColumn);
                rowIndex++;
            }
            PoiSheetSupport.autoSizeColumns(sheet, headerCol);
            wb.write(out);
            return out.toByteArray();
        }
    }

    public String suggestedExportFilename() {
        return "clients_export_" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".xlsx";
    }

    public byte[] buildImportTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Clients");
            Row header = sheet.createRow(0);
            for (int c = 0; c < TEMPLATE_HEADERS.length; c++) {
                header.createCell(c).setCellValue(TEMPLATE_HEADERS[c]);
            }
            Row sample = sheet.createRow(1);
            Object[] values = {
                1,
                "Rahul",
                "Sharma",
                "",
                "Engineer",
                "101 Sample Heights, Andheri West",
                "",
                "Mumbai",
                "9876543210",
                "",
                "rahul.sharma@example.com",
                "ABCDE1234F",
                "123456789012",
                "1990-06-15",
                "",
                ""
            };
            for (int c = 0; c < values.length; c++) {
                Object value = values[c];
                if (value instanceof Number number) {
                    sample.createCell(c).setCellValue(number.doubleValue());
                } else {
                    sample.createCell(c).setCellValue(String.valueOf(value));
                }
            }
            PoiSheetSupport.autoSizeColumns(sheet, TEMPLATE_HEADERS.length);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public int importForTenant(MultipartFile file) {
        UUID builderId = TenantContext.requireBuilderId();
        validateFile(file);
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        List<ClientImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = parse(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No client rows found. Use the Download sample Excel template (row 1 = headers).");
        }
        Instant now = Instant.now();
        for (ClientImportRow row : rows) {
            Client entity = new Client();
            entity.setBuilder(builder);
            entity.setFirstName(blankToEmpty(row.firstName()));
            entity.setLastName(emptyToNull(row.lastName()));
            entity.setCompanyName(emptyToNull(row.companyName()));
            entity.setOccupation(emptyToNull(row.occupation()));
            entity.setAddress1(emptyToNull(row.address1()));
            entity.setAddress2(emptyToNull(row.address2()));
            entity.setCity(emptyToNull(row.city()));
            entity.setMobile1(emptyToNull(row.phone1()));
            entity.setMobile2(emptyToNull(row.phone2()));
            entity.setEmail1(emptyToNull(row.email()));
            entity.setPanNumber(
                    row.panNumber() != null && !row.panNumber().isBlank()
                            ? row.panNumber().trim().toUpperCase(Locale.ROOT)
                            : null);
            entity.setAadhaarNumber(emptyToNull(row.aadhaarNumber()));
            entity.setDob(row.dob());
            entity.setDateOfMarriage(row.dateOfMarriage());
            entity.setParticulars(emptyToNull(row.particulars()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            clientRepository.save(entity);
        }
        return rows.size();
    }

    List<ClientImportRow> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, formatter, evaluator);
            Row header = sheet.getRow(headerRowIndex);
            int firstNameCol = findColumn(header, formatter, evaluator, "first_name", 1);
            int lastNameCol = findColumn(header, formatter, evaluator, "last_name", 2);
            int companyCol = findColumn(header, formatter, evaluator, "company", 3);
            int occupationCol = findColumn(header, formatter, evaluator, "occupation", 4);
            int address1Col = findColumn(header, formatter, evaluator, "address1", 5);
            int address2Col = findColumn(header, formatter, evaluator, "address2", 6);
            int cityCol = findColumn(header, formatter, evaluator, "city", 7);
            int phone1Col = findColumn(header, formatter, evaluator, "phone1", 8);
            int phone2Col = findColumn(header, formatter, evaluator, "phone2", 9);
            int emailCol = findColumn(header, formatter, evaluator, "email", 10);
            int panCol = findColumn(header, formatter, evaluator, "pan", 11);
            int aadhaarCol = findColumn(header, formatter, evaluator, "aadhaar", 12);
            int dobCol = findColumn(header, formatter, evaluator, "dob", 13);
            int domCol = findColumn(header, formatter, evaluator, "dom", 14);
            int particularsCol = findColumn(header, formatter, evaluator, "particulars", 15);

            List<ClientImportRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum() && rows.size() < MAX_ROWS; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (!rowHasClientData(row, formatter, evaluator, firstNameCol, lastNameCol, companyCol, occupationCol,
                        address1Col, address2Col, cityCol, phone1Col, phone2Col, emailCol, panCol, aadhaarCol, dobCol,
                        domCol, particularsCol)) {
                    continue;
                }
                int excelRow = r + 1;
                LocalDate dob = parseDate(row.getCell(dobCol), formatter, evaluator, excelRow, "Date of Birth");
                LocalDate dom =
                        parseDate(row.getCell(domCol), formatter, evaluator, excelRow, "Date of Marriage");

                rows.add(
                        new ClientImportRow(
                                cellText(row.getCell(firstNameCol), formatter, evaluator),
                                cellText(row.getCell(lastNameCol), formatter, evaluator),
                                cellText(row.getCell(companyCol), formatter, evaluator),
                                cellText(row.getCell(occupationCol), formatter, evaluator),
                                cellText(row.getCell(address1Col), formatter, evaluator),
                                cellText(row.getCell(address2Col), formatter, evaluator),
                                cellText(row.getCell(cityCol), formatter, evaluator),
                                cellText(row.getCell(phone1Col), formatter, evaluator),
                                cellText(row.getCell(phone2Col), formatter, evaluator),
                                cellText(row.getCell(emailCol), formatter, evaluator),
                                cellText(row.getCell(panCol), formatter, evaluator),
                                cellText(row.getCell(aadhaarCol), formatter, evaluator),
                                dob,
                                dom,
                                cellText(row.getCell(particularsCol), formatter, evaluator)));
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

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static boolean rowHasClientData(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int... columnIndexes) {
        for (int col : columnIndexes) {
            if (!cellText(row.getCell(col), formatter, evaluator).isBlank()) {
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
            boolean hasFirst = false;
            boolean hasMobile = false;
            for (Cell cell : row) {
                String v = cellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
                if (v.contains("first") && v.contains("name")) {
                    hasFirst = true;
                }
                if (v.contains("mobile")) {
                    hasMobile = true;
                }
            }
            if (hasFirst && hasMobile) {
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
                case "first_name" -> {
                    if (v.contains("first") && v.contains("name")) {
                        return cell.getColumnIndex();
                    }
                }
                case "last_name" -> {
                    if (v.contains("last") && v.contains("name")) {
                        return cell.getColumnIndex();
                    }
                }
                case "company" -> {
                    if (v.equals("company") || v.contains("company name")) {
                        return cell.getColumnIndex();
                    }
                }
                case "occupation" -> {
                    if (v.contains("occupation")) {
                        return cell.getColumnIndex();
                    }
                }
                case "address1" -> {
                    if (v.contains("address") && (v.contains("1") || v.endsWith("line 1"))) {
                        return cell.getColumnIndex();
                    }
                }
                case "address2" -> {
                    if (v.contains("address") && (v.contains("2") || v.endsWith("line 2"))) {
                        return cell.getColumnIndex();
                    }
                }
                case "city" -> {
                    if (v.equals("city")) {
                        return cell.getColumnIndex();
                    }
                }
                case "phone1" -> {
                    if (v.equals("phone 1")
                            || v.equals("phone1")
                            || (v.contains("mobile") && (v.contains("1") || v.equals("mobile")))
                            || (v.contains("phone") && v.contains("1") && !v.contains("2"))) {
                        return cell.getColumnIndex();
                    }
                }
                case "phone2" -> {
                    if (v.equals("phone 2")
                            || v.equals("phone2")
                            || (v.contains("mobile") && v.contains("2"))
                            || (v.contains("phone") && v.contains("2"))) {
                        return cell.getColumnIndex();
                    }
                }
                case "email" -> {
                    if ((v.equals("email") || v.contains("email 1") || v.equals("email1"))
                            && !v.contains("email 2")
                            && !v.equals("email2")) {
                        return cell.getColumnIndex();
                    }
                }
                case "pan" -> {
                    if (v.equals("pan")
                            || v.startsWith("pan ")
                            || v.contains("pan no")
                            || v.contains("pan number")) {
                        return cell.getColumnIndex();
                    }
                }
                case "aadhaar" -> {
                    if (v.contains("aadhaar") || v.contains("aadhar")) {
                        return cell.getColumnIndex();
                    }
                }
                case "dob" -> {
                    if (v.contains("birth") || v.equals("dob") || v.contains("date of birth")) {
                        return cell.getColumnIndex();
                    }
                }
                case "dom" -> {
                    if (v.contains("marriage")) {
                        return cell.getColumnIndex();
                    }
                }
                case "particulars" -> {
                    if (v.contains("particular")) {
                        return cell.getColumnIndex();
                    }
                }
                default -> {}
            }
        }
        return fallback;
    }

    private static LocalDate parseDate(
            Cell cell, DataFormatter formatter, FormulaEvaluator evaluator, int excelRow, String field) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = cellText(cell, formatter, evaluator);
        if (raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter pattern :
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE,
                        DateTimeFormatter.ofPattern("d/M/uuuu"),
                        DateTimeFormatter.ofPattern("d-M-uuuu"),
                        DateTimeFormatter.ofPattern("d.M.uuuu"),
                        DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                        DateTimeFormatter.ofPattern("dd-MM-uuuu"))) {
            try {
                return LocalDate.parse(raw, pattern);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        throw new IllegalArgumentException(
                "Row " + excelRow + ": " + field + " must be a date (e.g. 1990-06-15 or 15/06/1990).");
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private static String[] exportHeaderLabels(boolean includeProjectColumn) {
        if (!includeProjectColumn) {
            return TEMPLATE_HEADERS;
        }
        String[] headers = new String[TEMPLATE_HEADERS.length + 1];
        headers[0] = TEMPLATE_HEADERS[0];
        headers[1] = "Project";
        System.arraycopy(TEMPLATE_HEADERS, 1, headers, 2, TEMPLATE_HEADERS.length - 1);
        return headers;
    }

    private static void writeExportRow(Row row, int serial, Client client, boolean includeProjectColumn) {
        int col = 0;
        row.createCell(col++).setCellValue(serial);
        if (includeProjectColumn) {
            String project =
                    client.getBuilder() != null && client.getBuilder().getCompanyName() != null
                            ? client.getBuilder().getCompanyName()
                            : "";
            row.createCell(col++).setCellValue(project);
        }
        setCell(row, col++, client.getFirstName());
        setCell(row, col++, client.getLastName());
        setCell(row, col++, client.getCompanyName());
        setCell(row, col++, client.getOccupation());
        setCell(row, col++, client.getAddress1());
        setCell(row, col++, client.getAddress2());
        setCell(row, col++, client.getCity());
        setCell(row, col++, client.getMobile1());
        setCell(row, col++, client.getMobile2());
        setCell(row, col++, client.getEmail1());
        setCell(row, col++, client.getPanNumber());
        setCell(row, col++, client.getAadhaarNumber());
        setDateCell(row, col++, client.getDob());
        setDateCell(row, col++, client.getDateOfMarriage());
        setCell(row, col, client.getParticulars());
    }

    private static void setCell(Row row, int col, String value) {
        if (value != null && !value.isBlank()) {
            row.createCell(col).setCellValue(value.trim());
        }
    }

    private static void setDateCell(Row row, int col, LocalDate value) {
        if (value != null) {
            row.createCell(col).setCellValue(value.format(EXPORT_DATE));
        }
    }
}
