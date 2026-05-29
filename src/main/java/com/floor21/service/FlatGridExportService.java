package com.floor21.service;

import com.floor21.dto.FlatGridFlatDto;
import com.floor21.dto.FlatGridFloorDto;
import com.floor21.entity.Building;
import com.floor21.util.IndianRupeesFormatter;
import com.floor21.util.PoiSheetSupport;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlatGridExportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final String[] HEADERS = {
        "Floor", "Flat", "BHK", "Status", "Area (sqft)", "Base price", "Partner", "Owner / detail"
    };

    private final BuildingService buildingService;
    private final FlatService flatService;

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        List<FlatGridFloorDto> floors = flatService.getGridData(buildingId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            document.add(new Paragraph("Flat grid — " + nullToDash(building.getBuildingName()), titleFont));
            document.add(new Paragraph(" "));
            document.add(summaryParagraph(building, floors, labelFont, bodyFont));
            document.add(new Paragraph(" "));
            document.add(
                    new Paragraph(
                            "Legend: Available · Hold · Booked · Deactivated. "
                                    + "Other partners’ flats show limited detail.",
                            smallFont));
            document.add(new Paragraph(" "));

            if (floors.isEmpty()) {
                document.add(new Paragraph("No flats on this grid.", bodyFont));
            } else {
                PdfPTable table = new PdfPTable(HEADERS.length);
                table.setWidthPercentage(100f);
                table.setWidths(new float[] {7f, 10f, 8f, 11f, 11f, 13f, 14f, 26f});
                for (String h : HEADERS) {
                    table.addCell(headerCell(h, headerFont));
                }
                for (FlatGridFloorDto floor : floors) {
                    for (FlatGridFlatDto flat : floor.flats()) {
                        table.addCell(dataCell(String.valueOf(flat.floorNumber()), bodyFont, Element.ALIGN_CENTER));
                        table.addCell(dataCell(nullToDash(flat.flatNumber()), bodyFont));
                        table.addCell(dataCell(nullToDash(flat.bhkType()), bodyFont));
                        table.addCell(dataCell(statusLabel(flat), bodyFont));
                        table.addCell(
                                dataCell(
                                        flat.areaSqft() != null ? flat.areaSqft().stripTrailingZeros().toPlainString() : "—",
                                        bodyFont,
                                        Element.ALIGN_RIGHT));
                        table.addCell(dataCell(formatMoney(flat.basePrice()), bodyFont, Element.ALIGN_RIGHT));
                        table.addCell(dataCell(nullToDash(flat.assignedPartnerName()), bodyFont));
                        table.addCell(dataCell(ownerDetailForExport(flat), bodyFont));
                    }
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate flat grid PDF", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedPdfFilename(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        String name = building.getBuildingName() != null ? building.getBuildingName() : "building";
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.isBlank()) {
            safe = "building";
        }
        return "Flat_Grid_" + safe + "_" + LocalDate.now() + ".pdf";
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        List<FlatGridFloorDto> floors = flatService.getGridData(buildingId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Flat grid");
            org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            CellStyle labelStyle = wb.createCellStyle();
            labelStyle.setFont(boldFont);

            int rowIdx = 0;
            rowIdx = writeExcelSummary(sheet, rowIdx, building, floors, labelStyle);
            rowIdx++;
            Row headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }
            Map<String, CellStyle> rowStyles = new HashMap<>();
            for (FlatGridFloorDto floor : floors) {
                for (FlatGridFlatDto flat : floor.flats()) {
                    Row row = sheet.createRow(rowIdx++);
                    CellStyle rowStyle = excelRowStyle(wb, flat, rowStyles);
                    writeFlatExcelRow(row, flat, rowStyle);
                }
            }
            PoiSheetSupport.autoSizeColumns(sheet, HEADERS.length, 12000);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate flat grid Excel export", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedExcelFilename(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        String name = building.getBuildingName() != null ? building.getBuildingName() : "building";
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.isBlank()) {
            safe = "building";
        }
        return "Flat_Grid_" + safe + "_" + LocalDate.now() + ".xlsx";
    }

    @Transactional(readOnly = true)
    public byte[] exportVisualGridPdf(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        List<FlatGridFloorDto> floors = flatService.getGridData(buildingId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font floorLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            document.add(new Paragraph("Flat grid (visual) — " + nullToDash(building.getBuildingName()), titleFont));
            document.add(
                    new Paragraph(
                            "Exported on "
                                    + DATE_FMT.format(LocalDate.now())
                                    + " · "
                                    + floors.stream().mapToInt(f -> f.flats().size()).sum()
                                    + " flats",
                            metaFont));
            document.add(new Paragraph(" "));
            addVisualLegend(document);
            document.add(new Paragraph(" "));

            if (floors.isEmpty()) {
                document.add(new Paragraph("No flats on this grid.", metaFont));
            } else {
                for (FlatGridFloorDto floor : floors) {
                    addVisualFloorRow(document, floor, floorLabelFont);
                    document.add(new Paragraph(" ", metaFont));
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate visual flat grid PDF", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedVisualGridPdfFilename(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        String name = building.getBuildingName() != null ? building.getBuildingName() : "building";
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.isBlank()) {
            safe = "building";
        }
        return "Flat_Grid_Visual_" + safe + "_" + LocalDate.now() + ".pdf";
    }

    private static void addVisualLegend(Document document) throws DocumentException {
        Font legendFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        PdfPTable legend = new PdfPTable(6);
        legend.setWidthPercentage(72f);
        legend.setHorizontalAlignment(Element.ALIGN_LEFT);
        legend.setSpacingAfter(4f);
        addLegendSwatch(legend, "Available", CardStyle.available(), legendFont);
        addLegendSwatch(legend, "Hold", CardStyle.hold(), legendFont);
        addLegendSwatch(legend, "Booked", CardStyle.booked(), legendFont);
        addLegendSwatch(legend, "Deactivated", CardStyle.deactivated(), legendFont);
        addLegendSwatch(legend, "Parking", CardStyle.parking(), legendFont);
        addLegendSwatch(legend, "Other partner", CardStyle.otherPartner(), legendFont);
        document.add(legend);
    }

    private static void addLegendSwatch(PdfPTable legend, String label, CardStyle style, Font font) {
        PdfPCell swatch = new PdfPCell();
        swatch.setFixedHeight(12f);
        swatch.setBackgroundColor(style.background());
        swatch.setBorderColor(style.border());
        swatch.setBorderWidth(1.5f);
        swatch.setPadding(0f);
        legend.addCell(swatch);

        PdfPCell text = new PdfPCell(new Phrase(label, font));
        text.setBorder(Rectangle.NO_BORDER);
        text.setPaddingLeft(4f);
        text.setPaddingRight(10f);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        legend.addCell(text);
    }

    private static void addVisualFloorRow(Document document, FlatGridFloorDto floor, Font floorLabelFont)
            throws DocumentException {
        List<FlatGridFlatDto> flats = floor.flats();
        if (flats.isEmpty()) {
            return;
        }

        PdfPTable row = new PdfPTable(2);
        row.setWidthPercentage(100f);
        row.setWidths(new float[] {7f, 93f});
        row.setSpacingAfter(6f);

        PdfPCell floorCell = new PdfPCell(new Phrase(floor.label(), floorLabelFont));
        floorCell.setBorder(Rectangle.NO_BORDER);
        floorCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        floorCell.setRotation(90);
        floorCell.setPadding(4f);
        row.addCell(floorCell);

        int count = flats.size();
        float[] widths = new float[count];
        for (int i = 0; i < count; i++) {
            widths[i] = 1f;
        }
        PdfPTable cards = new PdfPTable(count);
        cards.setWidthPercentage(100f);
        cards.setWidths(widths);
        for (FlatGridFlatDto flat : flats) {
            cards.addCell(visualFlatCardCell(flat, count));
        }

        PdfPCell cardsWrap = new PdfPCell(cards);
        cardsWrap.setBorder(Rectangle.NO_BORDER);
        cardsWrap.setPadding(2f);
        row.addCell(cardsWrap);

        document.add(row);
    }

    private static PdfPCell visualFlatCardCell(FlatGridFlatDto flat, int flatsOnFloor) {
        CardStyle style = resolveCardStyle(flat);
        float numberSize = flatsOnFloor > 14 ? 7.5f : (flatsOnFloor > 10 ? 8.5f : 9.5f);
        float bodySize = flatsOnFloor > 14 ? 5.5f : (flatsOnFloor > 10 ? 6.5f : 7.5f);

        Font numberFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, numberSize, style.titleText());
        Font typeFont = FontFactory.getFont(FontFactory.HELVETICA, bodySize, style.bodyText());
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, bodySize - 0.5f, style.bodyText());
        Font partnerFont = FontFactory.getFont(FontFactory.HELVETICA, bodySize - 0.5f, new Color(99, 102, 241));
        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, bodySize - 0.5f, new Color(180, 83, 9));

        PdfPTable inner = new PdfPTable(2);
        inner.setWidthPercentage(100f);
        inner.setWidths(new float[] {38f, 62f});

        PdfPCell head = new PdfPCell();
        head.setBorder(Rectangle.NO_BORDER);
        head.setPadding(3f);
        head.setBackgroundColor(style.background());
        Paragraph headText = new Paragraph();
        headText.add(new Phrase(nullToDash(flat.flatNumber()), numberFont));
        headText.add(new Phrase("\n" + nullToDash(flat.bhkType()), typeFont));
        if (flat.assignedPartnerName() != null && !flat.assignedPartnerName().isBlank()) {
            headText.add(new Phrase("\n" + truncate(flat.assignedPartnerName(), 14), partnerFont));
        }
        if ("CANCELLED".equals(flat.status())) {
            headText.add(new Phrase("\nDEACTIVATED", statusFont));
        }
        head.addElement(headText);
        inner.addCell(head);

        PdfPCell owner = new PdfPCell();
        owner.setBorder(Rectangle.NO_BORDER);
        owner.setPadding(3f);
        owner.setBackgroundColor(style.background());
        owner.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String ownerLine = visualOwnerPrimary(flat);
        String detailLine = visualOwnerSecondary(flat);
        Paragraph ownerText = new Paragraph();
        if (!ownerLine.isBlank()) {
            ownerText.add(new Phrase(truncate(ownerLine, 28), numberFont));
        }
        if (!detailLine.isBlank()) {
            ownerText.add(new Phrase("\n" + truncate(detailLine, 32), metaFont));
        }
        if (ownerLine.isBlank() && detailLine.isBlank()) {
            ownerText.add(new Phrase(" ", typeFont));
        }
        owner.addElement(ownerText);
        inner.addCell(owner);

        PdfPCell card = new PdfPCell(inner);
        card.setBackgroundColor(style.background());
        card.setBorderColor(style.border());
        card.setBorderWidth(2f);
        card.setPadding(0f);
        card.setMinimumHeight(flatsOnFloor > 12 ? 36f : 44f);
        return card;
    }

    private static String visualOwnerPrimary(FlatGridFlatDto flat) {
        if (!flat.bookableByCurrentUser()) {
            if ("BOOKED".equals(flat.status())) {
                return "Booked";
            }
            if ("CANCELLED".equals(flat.status())) {
                return "Deactivated";
            }
            if ("HOLD".equals(flat.status())) {
                return "Hold";
            }
            if (Boolean.TRUE.equals(flat.parking())) {
                return "Parking";
            }
            return "";
        }
        if ("CANCELLED".equals(flat.status())) {
            return "Deactivated";
        }
        if (flat.ownerDisplay() != null && !flat.ownerDisplay().isBlank()) {
            return flat.ownerDisplay().trim();
        }
        if ("HOLD".equals(flat.status())) {
            return "Hold";
        }
        if ("AVAILABLE".equals(flat.status())) {
            return "Available";
        }
        return "";
    }

    private static String visualOwnerSecondary(FlatGridFlatDto flat) {
        if (!flat.bookableByCurrentUser() || "CANCELLED".equals(flat.status())) {
            return "";
        }
        if (flat.ownerDetail() != null && !flat.ownerDetail().isBlank()) {
            return flat.ownerDetail().trim();
        }
        return "";
    }

    private static CardStyle resolveCardStyle(FlatGridFlatDto flat) {
        if (Boolean.TRUE.equals(flat.parking())) {
            return CardStyle.parking();
        }
        if (!flat.bookableByCurrentUser()) {
            return CardStyle.otherPartner();
        }
        return switch (flat.status() != null ? flat.status() : "") {
            case "AVAILABLE" -> CardStyle.available();
            case "BOOKED" -> CardStyle.booked();
            case "CANCELLED" -> CardStyle.deactivated();
            case "HOLD" -> CardStyle.hold();
            default -> CardStyle.hold();
        };
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen - 1) + "…";
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private record CardStyle(Color background, Color border, Color titleText, Color bodyText) {
        static CardStyle available() {
            return new CardStyle(rgb(209, 231, 221), rgb(25, 135, 84), rgb(0, 0, 0), rgb(33, 37, 41));
        }

        static CardStyle booked() {
            return new CardStyle(rgb(207, 226, 255), rgb(13, 110, 253), rgb(5, 44, 101), rgb(13, 71, 161));
        }

        static CardStyle hold() {
            return new CardStyle(rgb(255, 243, 205), rgb(255, 193, 7), rgb(0, 0, 0), rgb(33, 37, 41));
        }

        static CardStyle deactivated() {
            return new CardStyle(rgb(248, 215, 218), rgb(220, 53, 69), rgb(132, 32, 41), rgb(132, 32, 41));
        }

        static CardStyle parking() {
            return new CardStyle(rgb(226, 227, 229), rgb(108, 117, 125), rgb(73, 80, 87), rgb(108, 117, 125));
        }

        static CardStyle otherPartner() {
            return new CardStyle(rgb(236, 239, 241), rgb(176, 190, 197), rgb(108, 117, 125), rgb(108, 117, 125));
        }
    }

    private static Paragraph summaryParagraph(
            Building building, List<FlatGridFloorDto> floors, Font labelFont, Font bodyFont) {
        int flatCount = floors.stream().mapToInt(f -> f.flats().size()).sum();
        long booked =
                floors.stream()
                        .flatMap(f -> f.flats().stream())
                        .filter(f -> "BOOKED".equals(f.status()))
                        .count();
        long available =
                floors.stream()
                        .flatMap(f -> f.flats().stream())
                        .filter(f -> "AVAILABLE".equals(f.status()))
                        .count();
        long hold =
                floors.stream()
                        .flatMap(f -> f.flats().stream())
                        .filter(f -> "HOLD".equals(f.status()))
                        .count();
        long deactivated =
                floors.stream()
                        .flatMap(f -> f.flats().stream())
                        .filter(f -> "CANCELLED".equals(f.status()))
                        .count();

        StringBuilder sb = new StringBuilder();
        appendLine(sb, "Building", nullToDash(building.getBuildingName()));
        appendLine(sb, "Total flats", String.valueOf(flatCount));
        appendLine(sb, "Available", String.valueOf(available));
        appendLine(sb, "Hold", String.valueOf(hold));
        appendLine(sb, "Booked", String.valueOf(booked));
        appendLine(sb, "Deactivated", String.valueOf(deactivated));
        appendLine(sb, "Exported on", DATE_FMT.format(LocalDate.now()));

        Paragraph p = new Paragraph();
        for (String line : sb.toString().split("\n")) {
            if (line.contains(":")) {
                int colon = line.indexOf(':');
                p.add(new Phrase(line.substring(0, colon + 1) + " ", labelFont));
                p.add(new Phrase(line.substring(colon + 1).trim() + "\n", bodyFont));
            } else {
                p.add(new Phrase(line + "\n", bodyFont));
            }
        }
        return p;
    }

    private static int writeExcelSummary(
            Sheet sheet, int startRow, Building building, List<FlatGridFloorDto> floors, CellStyle labelStyle) {
        int flatCount = floors.stream().mapToInt(f -> f.flats().size()).sum();
        long booked =
                floors.stream().flatMap(f -> f.flats().stream()).filter(f -> "BOOKED".equals(f.status())).count();
        long available =
                floors.stream().flatMap(f -> f.flats().stream()).filter(f -> "AVAILABLE".equals(f.status())).count();
        long hold =
                floors.stream().flatMap(f -> f.flats().stream()).filter(f -> "HOLD".equals(f.status())).count();
        long deactivated =
                floors.stream().flatMap(f -> f.flats().stream()).filter(f -> "CANCELLED".equals(f.status())).count();

        String[][] lines = {
            {"Flat grid export", ""},
            {"Building", nullToDash(building.getBuildingName())},
            {"Total flats", String.valueOf(flatCount)},
            {"Available", String.valueOf(available)},
            {"Hold", String.valueOf(hold)},
            {"Booked", String.valueOf(booked)},
            {"Deactivated", String.valueOf(deactivated)},
            {"Exported on", DATE_FMT.format(LocalDate.now())},
        };
        int rowIdx = startRow;
        for (int i = 0; i < lines.length; i++) {
            Row row = sheet.createRow(rowIdx++);
            Cell label = row.createCell(0);
            label.setCellValue(lines[i][0]);
            if (i == 0) {
                label.setCellStyle(labelStyle);
            }
            row.createCell(1).setCellValue(lines[i][1]);
        }
        return rowIdx;
    }

    private static void writeFlatExcelRow(Row row, FlatGridFlatDto flat, CellStyle rowStyle) {
        Object[] values = {
            flat.floorNumber(),
            nullToDash(flat.flatNumber()),
            nullToDash(flat.bhkType()),
            statusLabel(flat),
            flat.areaSqft() != null ? flat.areaSqft().doubleValue() : null,
            flat.basePrice() != null ? flat.basePrice().doubleValue() : null,
            nullToDash(flat.assignedPartnerName()),
            ownerDetailForExport(flat),
        };
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.createCell(c);
            Object value = values[c];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(String.valueOf(value));
            }
            if (rowStyle != null) {
                cell.setCellStyle(rowStyle);
            }
        }
    }

    private static CellStyle excelRowStyle(XSSFWorkbook wb, FlatGridFlatDto flat, Map<String, CellStyle> cache) {
        String key = excelStyleKey(flat);
        return cache.computeIfAbsent(
                key,
                k -> {
                    XSSFCellStyle style = wb.createCellStyle();
                    Color bg = resolveCardStyle(flat).background();
                    style.setFillForegroundColor(
                            new XSSFColor(new byte[] {(byte) bg.getRed(), (byte) bg.getGreen(), (byte) bg.getBlue()}, null));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    return style;
                });
    }

    private static String excelStyleKey(FlatGridFlatDto flat) {
        if (Boolean.TRUE.equals(flat.parking())) {
            return "parking";
        }
        if (!flat.bookableByCurrentUser()) {
            return "other";
        }
        return flat.status() != null ? flat.status() : "hold";
    }

    private static String statusLabel(FlatGridFlatDto flat) {
        if (Boolean.TRUE.equals(flat.parking())) {
            return "Parking";
        }
        return switch (flat.status() != null ? flat.status() : "") {
            case "AVAILABLE" -> "Available";
            case "HOLD" -> "Hold";
            case "BOOKED" -> "Booked";
            case "CANCELLED" -> "Deactivated";
            default -> nullToDash(flat.status());
        };
    }

    private static String ownerDetailForExport(FlatGridFlatDto flat) {
        if (!flat.bookableByCurrentUser()) {
            if ("BOOKED".equals(flat.status())) {
                return "Booked";
            }
            if ("CANCELLED".equals(flat.status())) {
                return "Deactivated";
            }
            if ("HOLD".equals(flat.status())) {
                return "Hold";
            }
            return "—";
        }
        if ("CANCELLED".equals(flat.status())) {
            return "Deactivated";
        }
        StringBuilder sb = new StringBuilder();
        if (flat.ownerDisplay() != null && !flat.ownerDisplay().isBlank()) {
            sb.append(flat.ownerDisplay().trim());
        }
        if (flat.ownerDetail() != null && !flat.ownerDetail().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append(flat.ownerDetail().trim());
        }
        if (flat.bookingCode() != null && !flat.bookingCode().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append("Booking ").append(flat.bookingCode().trim());
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(value).append('\n');
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4f);
        return cell;
    }

    private static PdfPCell dataCell(String text, Font font) {
        return dataCell(text, font, Element.ALIGN_LEFT);
    }

    private static PdfPCell dataCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(3f);
        return cell;
    }

    private static String formatMoney(BigDecimal amount) {
        return amount != null ? IndianRupeesFormatter.formatFigures(amount) : "—";
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
