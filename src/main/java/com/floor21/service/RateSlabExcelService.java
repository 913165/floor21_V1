package com.floor21.service;

import com.floor21.dto.RateSlabImportRow;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Slab;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.util.PoiSheetSupport;
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
public class RateSlabExcelService {

    private static final int MAX_ROWS = 500;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final SlabRepository slabRepository;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;

    public byte[] buildImportTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Milestone settings");
            Row header = sheet.createRow(0);
            String[] headers = {"Sno", "Slab Name", "Percent (%)", "Active"};
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            Object[][] samples = {
                {1, "Initial booking amount", 10, "Yes"},
                {2, "On or after execution of this Agreement", 20, "Yes"},
                {3, "On completion of the Plinth work of the building", 15, "Yes"},
                {4, "On or before completion 2nd Slab", 2.5, "Yes"},
                {5, "On handing over possession of Unit or receipt of Occupancy Certificate", 5, "Yes"}
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(((Number) samples[r][0]).intValue());
                row.createCell(1).setCellValue((String) samples[r][1]);
                row.createCell(2).setCellValue(((Number) samples[r][2]).doubleValue());
                row.createCell(3).setCellValue((String) samples[r][3]);
            }
            PoiSheetSupport.autoSizeColumns(sheet, headers.length);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public int importForBuilder(UUID builderId, UUID buildingId, MultipartFile file, boolean replaceExisting) {
        if (builderId == null) {
            throw new IllegalArgumentException("Select a builder before importing.");
        }
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
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .orElseThrow(() -> new IllegalArgumentException("Builder not found."));
        Building building = null;
        if (buildingId != null) {
            building =
                    buildingRepository
                            .findByIdAndBuilder_Id(buildingId, builderId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Building not found for the selected builder."));
        }
        List<RateSlabImportRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = parse(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No milestone rows found. Use columns Slab Name and Percent (%) (see Download Excel template).");
        }
        if (replaceExisting) {
            if (building != null) {
                slabRepository.deleteByBuilding_Id(buildingId);
            } else {
                slabRepository.deleteByBuilder_IdAndBuildingIsNull(builderId);
            }
        }
        Instant now = Instant.now();
        for (RateSlabImportRow row : rows) {
            Slab entity = new Slab();
            entity.setBuilder(builder);
            entity.setBuilding(building);
            entity.setSortOrder(row.sortOrder());
            entity.setSlabName(row.slabName());
            entity.setSuggestedPercent(row.suggestedPercent());
            entity.setActive(row.active());
            entity.setCreatedAt(now);
            slabRepository.save(entity);
        }
        return rows.size();
    }

    List<RateSlabImportRow> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int headerRowIndex = findHeaderRow(sheet, formatter, evaluator);
            Row header = sheet.getRow(headerRowIndex);
            int snoCol = findColumn(header, formatter, evaluator, "sno", 0);
            int nameCol = findColumn(header, formatter, evaluator, "name", 1);
            int percentCol = findColumn(header, formatter, evaluator, "percent", 2);
            int activeCol = findColumn(header, formatter, evaluator, "active", 3);
            List<RateSlabImportRow> rows = new ArrayList<>();
            int fallbackOrder = 0;
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum() && rows.size() < MAX_ROWS; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String slabName = cellText(row.getCell(nameCol), formatter, evaluator);
                if (slabName.isBlank() || isSummaryRow(slabName)) {
                    continue;
                }
                String percentRaw = cellText(row.getCell(percentCol), formatter, evaluator);
                BigDecimal percent = parsePercent(percentRaw);
                if (percent == null) {
                    throw new IllegalArgumentException(
                            "Row " + (r + 1) + ": enter Percent (%) greater than or equal to zero.");
                }
                String activeRaw = cellText(row.getCell(activeCol), formatter, evaluator);
                boolean active = parseActive(activeRaw);
                int sortOrder = parseSortOrder(cellText(row.getCell(snoCol), formatter, evaluator), fallbackOrder);
                fallbackOrder++;
                rows.add(new RateSlabImportRow(sortOrder, slabName.trim(), percent, active));
            }
            return rows;
        }
    }

    private static boolean isSummaryRow(String slabName) {
        String normalized = slabName.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("total") || normalized.equals("balance");
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int last = Math.min(sheet.getLastRowNum(), 10);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean hasName = false;
            boolean hasPercent = false;
            for (Cell cell : row) {
                String v = cellText(cell, formatter, evaluator).toLowerCase(Locale.ROOT);
                if ((v.contains("slab") && v.contains("name"))
                        || (v.equals("slab") && !v.contains("date") && !v.contains("amount"))) {
                    hasName = true;
                }
                if (v.contains("percent") || v.equals("%")) {
                    hasPercent = true;
                }
            }
            if (hasName && hasPercent) {
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
                case "sno" -> {
                    if (v.equals("sno") || v.startsWith("s no") || v.equals("sr no") || v.equals("#")) {
                        return cell.getColumnIndex();
                    }
                }
                case "name" -> {
                    if ((v.contains("slab") && v.contains("name"))
                            || (v.equals("slab") && !v.contains("date") && !v.contains("amount"))) {
                        return cell.getColumnIndex();
                    }
                }
                case "percent" -> {
                    if (v.contains("percent") || v.equals("%")) {
                        return cell.getColumnIndex();
                    }
                }
                case "active" -> {
                    if (v.contains("active")) {
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

    private static int parseSortOrder(String raw, int fallbackZeroBased) {
        if (raw == null || raw.isBlank()) {
            return fallbackZeroBased + 1;
        }
        try {
            int value = Integer.parseInt(raw.replace(",", "").trim());
            return value > 0 ? value : fallbackZeroBased + 1;
        } catch (NumberFormatException ex) {
            return fallbackZeroBased + 1;
        }
    }

    private static BigDecimal parsePercent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace("%", "").replace(",", "").trim();
        try {
            BigDecimal value = new BigDecimal(normalized);
            if (value.signum() < 0) {
                return null;
            }
            return value.setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean parseActive(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return !v.equals("n") && !v.equals("no") && !v.equals("false") && !v.equals("0");
    }
}
