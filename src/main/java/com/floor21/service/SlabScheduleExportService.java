package com.floor21.service;

import com.floor21.dto.SlabLedgerRowType;
import com.floor21.dto.SlabScheduleLedgerRow;
import com.floor21.dto.SlabScheduleLedgerSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.util.IndianRupeesFormatter;
import com.floor21.util.PoiSheetSupport;
import com.floor21.security.TenantContext;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlabScheduleExportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final String[] LEDGER_HEADERS = {
        "Date", "Slab", "Check No", "Amount", "Receipt", "GST", "Balance", "Days", "Interest", "Info", "Remark"
    };

    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final SlabScheduleLedgerService slabScheduleLedgerService;
    private final BookingOwnerService bookingOwnerService;

    @Transactional(readOnly = true)
    public byte[] exportExcel(UUID bookingId) {
        return exportExcel(bookingId, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(UUID bookingId, UUID builderId) {
        ExportContext ctx = loadContext(bookingId, builderId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payment schedule");
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);

            int rowIdx = 0;
            rowIdx = writeSummaryBlock(sheet, rowIdx, ctx, boldStyle);
            rowIdx++;
            Row headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < LEDGER_HEADERS.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(LEDGER_HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }
            for (SlabScheduleLedgerRow ledgerRow : ctx.rows()) {
                Row row = sheet.createRow(rowIdx++);
                boolean milestone = ledgerRow.rowType() == SlabLedgerRowType.SLAB_TOTAL;
                writeLedgerCells(row, ledgerRow, milestone ? boldStyle : null);
            }
            if (ctx.summary() != null) {
                Row totalRow = sheet.createRow(rowIdx++);
                writeTotalRow(totalRow, ctx.summary(), boldStyle);
            }
            PoiSheetSupport.autoSizeColumns(sheet, LEDGER_HEADERS.length);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate Excel export", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID bookingId) {
        return exportPdf(bookingId, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID bookingId, UUID builderId) {
        ExportContext ctx = loadContext(bookingId, builderId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

            document.add(new Paragraph("Slab payment schedule", titleFont));
            document.add(new Paragraph(" "));
            document.add(summaryParagraph(ctx, labelFont, bodyFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(LEDGER_HEADERS.length);
            table.setWidthPercentage(100f);
            table.setWidths(new float[] {9f, 17f, 11f, 9f, 9f, 8f, 9f, 6f, 8f, 10f, 8f});
            for (String h : LEDGER_HEADERS) {
                table.addCell(headerCell(h, headerFont));
            }
            for (SlabScheduleLedgerRow ledgerRow : ctx.rows()) {
                boolean milestone = ledgerRow.rowType() == SlabLedgerRowType.SLAB_TOTAL;
                Font rowFont = milestone ? headerFont : bodyFont;
                table.addCell(dataCell(formatDate(ledgerRow.date()), rowFont));
                table.addCell(dataCell(nullToDash(ledgerRow.slabLabel()), rowFont));
                table.addCell(dataCell(nullToDash(ledgerRow.chequeLabel()), rowFont));
                table.addCell(dataCell(formatMoney(ledgerRow.amountDue()), rowFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(ledgerRow.receiptAmount()), rowFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(ledgerRow.gstAmount()), rowFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(ledgerRow.balance()), rowFont, Element.ALIGN_RIGHT));
                table.addCell(
                        dataCell(ledgerRow.days() != null ? ledgerRow.days().toString() : "—", rowFont, Element.ALIGN_CENTER));
                table.addCell(dataCell(formatMoney(ledgerRow.interest()), rowFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(nullToDash(ledgerRow.info()), rowFont));
                table.addCell(dataCell(nullToDash(ledgerRow.remark()), rowFont));
            }
            if (ctx.summary() != null) {
                SlabScheduleLedgerSummary s = ctx.summary();
                table.addCell(dataCell("", headerFont));
                table.addCell(dataCell("Total", headerFont));
                table.addCell(dataCell("—", headerFont));
                table.addCell(dataCell(formatMoney(s.totalAmountDue()), headerFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(s.totalReceipts()), headerFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(s.totalGst()), headerFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell(formatMoney(s.totalBalance()), headerFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell("—", headerFont, Element.ALIGN_CENTER));
                table.addCell(dataCell(formatMoney(s.totalInterest()), headerFont, Element.ALIGN_RIGHT));
                table.addCell(dataCell("—", headerFont));
                table.addCell(dataCell("—", headerFont));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate PDF export", ex);
        }
    }

    @Transactional(readOnly = true)
    public String suggestedExcelFilename(UUID bookingId) {
        return suggestedExcelFilename(bookingId, null);
    }

    @Transactional(readOnly = true)
    public String suggestedExcelFilename(UUID bookingId, UUID builderId) {
        return baseFilename(bookingId, builderId) + ".xlsx";
    }

    @Transactional(readOnly = true)
    public String suggestedPdfFilename(UUID bookingId) {
        return suggestedPdfFilename(bookingId, null);
    }

    @Transactional(readOnly = true)
    public String suggestedPdfFilename(UUID bookingId, UUID builderId) {
        return baseFilename(bookingId, builderId) + ".pdf";
    }

    private ExportContext loadContext(UUID bookingId, UUID builderId) {
        Booking booking;
        List<SlabScheduleLedgerRow> rows;
        if (TenantContext.getBuilderIdOrNull() != null) {
            booking = bookingPaymentSlabService.getBookingForSchedule(bookingId);
            rows = slabScheduleLedgerService.buildLedger(bookingId);
        } else {
            if (builderId == null) {
                throw new IllegalArgumentException("Select a project/building to export.");
            }
            booking = bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
            rows = slabScheduleLedgerService.buildLedgerReadOnly(bookingId, builderId);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No payment schedule is available for this booking. Create the slab schedule first.");
        }
        SlabScheduleLedgerSummary summary = slabScheduleLedgerService.summarizeLedger(rows);
        BigDecimal base = bookingPaymentSlabService.baseConsideration(booking);
        return new ExportContext(booking, rows, summary, base, bookingOwnerService.ownersDisplayName(booking));
    }

    private String baseFilename(UUID bookingId) {
        return baseFilename(bookingId, null);
    }

    private String baseFilename(UUID bookingId, UUID builderId) {
        Booking booking =
                TenantContext.getBuilderIdOrNull() != null
                        ? bookingPaymentSlabService.getBookingForSchedule(bookingId)
                        : bookingPaymentSlabService.getBookingForScheduleReadOnly(bookingId, builderId);
        String code = booking.getBookingCode() != null ? booking.getBookingCode() : bookingId.toString();
        String safe = code.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Slab_Schedule_" + safe + "_" + LocalDate.now();
    }

    private static int writeSummaryBlock(Sheet sheet, int startRow, ExportContext ctx, CellStyle boldStyle) {
        Booking booking = ctx.booking();
        Flat flat = booking.getFlat();
        Building building = flat != null ? flat.getBuilding() : null;
        String[][] lines = {
            {"Slab payment schedule export", ""},
            {"Booking code", nullToDash(booking.getBookingCode())},
            {"Owners", nullToDash(ctx.ownersDisplayName())},
            {
                "Flat",
                flat != null
                        ? nullToDash(flat.getFlatNumber())
                                + (flat.getBhkType() != null ? " (" + flat.getBhkType() + ")" : "")
                        : "—"
            },
            {"Building", building != null ? nullToDash(building.getBuildingName()) : "—"},
            {
                "Consideration / flat base",
                ctx.baseAmount() != null ? IndianRupeesFormatter.formatFigures(ctx.baseAmount()) : "—"
            },
            {
                "Booking date",
                booking.getBookingDate() != null ? DATE_FMT.format(booking.getBookingDate()) : "—"
            },
            {"Exported on", DATE_FMT.format(LocalDate.now())},
        };
        int rowIdx = startRow;
        for (int i = 0; i < lines.length; i++) {
            Row row = sheet.createRow(rowIdx++);
            Cell label = row.createCell(0);
            label.setCellValue(lines[i][0]);
            if (i == 0) {
                label.setCellStyle(boldStyle);
            }
            row.createCell(1).setCellValue(lines[i][1]);
        }
        return rowIdx;
    }

    private static Paragraph summaryParagraph(ExportContext ctx, Font labelFont, Font bodyFont) {
        Booking booking = ctx.booking();
        Flat flat = booking.getFlat();
        Building building = flat != null ? flat.getBuilding() : null;
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "Booking code", nullToDash(booking.getBookingCode()));
        appendLine(sb, "Owners", nullToDash(ctx.ownersDisplayName()));
        appendLine(
                sb,
                "Flat",
                flat != null
                        ? nullToDash(flat.getFlatNumber())
                                + (flat.getBhkType() != null ? " (" + flat.getBhkType() + ")" : "")
                        : "—");
        appendLine(sb, "Building", building != null ? nullToDash(building.getBuildingName()) : "—");
        appendLine(
                sb,
                "Consideration / flat base",
                ctx.baseAmount() != null ? IndianRupeesFormatter.formatFigures(ctx.baseAmount()) : "—");
        appendLine(
                sb,
                "Booking date",
                booking.getBookingDate() != null ? DATE_FMT.format(booking.getBookingDate()) : "—");
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

    private static void appendLine(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(value).append('\n');
    }

    private static void writeLedgerCells(Row row, SlabScheduleLedgerRow ledgerRow, CellStyle style) {
        Object[] values = {
            formatDate(ledgerRow.date()),
            nullToDash(ledgerRow.slabLabel()),
            nullToDash(ledgerRow.chequeLabel()),
            formatMoney(ledgerRow.amountDue()),
            formatMoney(ledgerRow.receiptAmount()),
            formatMoney(ledgerRow.gstAmount()),
            formatMoney(ledgerRow.balance()),
            ledgerRow.days() != null ? ledgerRow.days().toString() : "—",
            formatMoney(ledgerRow.interest()),
            nullToDash(ledgerRow.info()),
            nullToDash(ledgerRow.remark()),
        };
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(String.valueOf(values[c]));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private static void writeTotalRow(Row row, SlabScheduleLedgerSummary summary, CellStyle boldStyle) {
        row.createCell(0).setCellValue("");
        Cell label = row.createCell(1);
        label.setCellValue("Total");
        label.setCellStyle(boldStyle);
        row.createCell(2).setCellValue("—");
        row.createCell(3).setCellValue(formatMoney(summary.totalAmountDue()));
        row.createCell(4).setCellValue(formatMoney(summary.totalReceipts()));
        row.createCell(5).setCellValue(formatMoney(summary.totalGst()));
        row.createCell(6).setCellValue(formatMoney(summary.totalBalance()));
        row.createCell(7).setCellValue("—");
        row.createCell(8).setCellValue(formatMoney(summary.totalInterest()));
        row.createCell(9).setCellValue("—");
        row.createCell(10).setCellValue("—");
        for (int c = 3; c <= 8; c++) {
            row.getCell(c).setCellStyle(boldStyle);
        }
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

    private static String formatDate(LocalDate date) {
        return date != null ? DATE_FMT.format(date) : "—";
    }

    private static String formatMoney(BigDecimal amount) {
        return amount != null ? IndianRupeesFormatter.formatFigures(amount) : "—";
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private record ExportContext(
            Booking booking,
            List<SlabScheduleLedgerRow> rows,
            SlabScheduleLedgerSummary summary,
            BigDecimal baseAmount,
            String ownersDisplayName) {}
}
