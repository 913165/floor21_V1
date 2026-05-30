package com.floor21.service;

import com.floor21.dto.PaymentMilestoneImportRow;
import com.floor21.util.PoiSheetSupport;
import com.floor21.entity.Building;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.PaymentSlabTemplateRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
public class PaymentMilestoneExcelService {

    private static final int MAX_ROWS = 200;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final PaymentSlabTemplateRepository paymentSlabTemplateRepository;
    private final BuildingRepository buildingRepository;

    public byte[] buildImportTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payment milestones");
            Row header = sheet.createRow(0);
            String[] headers = {
                "Sno",
                "Slab Date (DD)",
                "Slab Date (MM)",
                "Slab Date (YYYY)",
                "Slab",
                "Percent",
                "Agree Slab Amount",
                "Extra Slab Amount"
            };
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue(1);
            sample.createCell(4).setCellValue("On completion of the plinth work of the building");
            sample.createCell(5).setCellValue(10);
            PoiSheetSupport.autoSizeColumns(sheet, headers.length);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public int importForBuilding(UUID buildingId, MultipartFile file, boolean replaceExisting) {
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
        List<PaymentMilestoneImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = parse(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No milestone rows found. Use columns Slab and Percent (see Download Excel template).");
        }
        Building building =
                buildingRepository
                        .findByIdWithBuilder(buildingId)
                        .orElseThrow(() -> new IllegalArgumentException("Building not found"));
        if (replaceExisting) {
            paymentSlabTemplateRepository.deleteByBuilding_Id(buildingId);
        }
        Instant now = Instant.now();
        for (PaymentMilestoneImportRow row : rows) {
            PaymentSlabTemplate entity = new PaymentSlabTemplate();
            entity.setBuilding(building);
            entity.setBuilder(building.getBuilder());
            entity.setCreatedAt(now);
            entity.setSortOrder(row.sortOrder());
            entity.setMilestoneLabel(row.milestoneLabel());
            entity.setSuggestedPercent(row.suggestedPercent());
            entity.setActive(true);
            paymentSlabTemplateRepository.save(entity);
        }
        return rows.size();
    }

    List<PaymentMilestoneImportRow> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, formatter, evaluator);
            int slabCol = 4;
            int percentCol = 5;
            int snoCol = 0;
            Row header = sheet.getRow(headerRowIndex);
            if (header != null) {
                slabCol = findColumn(header, formatter, evaluator, "slab", slabCol);
                percentCol = findColumn(header, formatter, evaluator, "percent", percentCol);
                snoCol = findColumn(header, formatter, evaluator, "sno", snoCol);
            }
            List<PaymentMilestoneImportRow> rows = new ArrayList<>();
            int dataStart = headerRowIndex + 1;
            int fallbackOrder = 0;
            for (int r = dataStart; r <= sheet.getLastRowNum() && rows.size() < MAX_ROWS; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String slab = cellText(row.getCell(slabCol), formatter, evaluator);
                if (slab.isBlank()) {
                    continue;
                }
                if ("balance".equalsIgnoreCase(slab.trim())) {
                    continue;
                }
                String percentRaw = cellText(row.getCell(percentCol), formatter, evaluator);
                BigDecimal percent = parsePercent(percentRaw);
                int sortOrder = parseSortOrder(cellText(row.getCell(snoCol), formatter, evaluator), fallbackOrder);
                fallbackOrder++;
                rows.add(new PaymentMilestoneImportRow(sortOrder, slab.trim(), percent));
            }
            return rows;
        }
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), 10);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean hasSlab = false;
            boolean hasPercent = false;
            for (Cell cell : row) {
                String v = cellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
                if (v.contains("slab") && !v.contains("amount") && !v.contains("date")) {
                    hasSlab = true;
                }
                if (v.contains("percent") || v.equals("%")) {
                    hasPercent = true;
                }
            }
            if (hasSlab && hasPercent) {
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
            if (keyword.equals("slab")) {
                if (v.equals("slab") || (v.contains("slab") && !v.contains("date") && !v.contains("amount"))) {
                    return cell.getColumnIndex();
                }
            } else if (keyword.equals("percent")) {
                if (v.contains("percent") || v.equals("%")) {
                    return cell.getColumnIndex();
                }
            } else if (keyword.equals("sno") && (v.equals("sno") || v.startsWith("s no") || v.equals("#"))) {
                return cell.getColumnIndex();
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

    private static int parseSortOrder(String raw, int fallbackIndex) {
        if (raw == null || raw.isBlank()) {
            return fallbackIndex;
        }
        try {
            String digits = raw.replaceAll("[^0-9.-]", "");
            if (digits.isBlank()) {
                return fallbackIndex;
            }
            return (int) Math.round(Double.parseDouble(digits));
        } catch (NumberFormatException ex) {
            return fallbackIndex;
        }
    }

    private static BigDecimal parsePercent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replace("%", "").replace(",", "").trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid percent value: " + raw);
        }
    }
}
