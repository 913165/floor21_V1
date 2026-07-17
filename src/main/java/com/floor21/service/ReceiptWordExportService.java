package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.entity.Receipt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptWordExportService {

    private static final String FONT = "Bookman Old Style";
    /** Word line spacing: 240 = single, 360 = 1.5 */
    private static final BigInteger LINE_SPACING_15 = BigInteger.valueOf(360);
    /** ~1 inch top margin so letterhead / user edits have room above receipt body. */
    private static final BigInteger PAGE_MARGIN_TOP = BigInteger.valueOf(1440);
    private static final BigInteger PAGE_MARGIN_SIDE = BigInteger.valueOf(1080);
    private static final BigInteger PAGE_MARGIN_BOTTOM = BigInteger.valueOf(1080);

    private final ReceiptPrintService receiptPrintService;

    @Transactional(readOnly = true)
    public byte[] generate(Receipt receipt, boolean allOwners) {
        return generateCombined(List.of(receipt), allOwners);
    }

    @Transactional(readOnly = true)
    public byte[] generateCombined(List<Receipt> receipts, boolean allOwners) {
        ReceiptLetterView view = receiptPrintService.buildCombinedLetterView(receipts, allOwners);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocument(doc, view);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate receipt Word document", ex);
        }
    }

    public String suggestedFilename(Receipt receipt) {
        return suggestedFilename(receipt, false);
    }

    public String suggestedFilename(Receipt receipt, boolean combined) {
        String recNo =
                receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()
                        ? receipt.getReceiptNumber().trim()
                        : receipt.getId().toString();
        String safe = recNo.replaceAll("[^a-zA-Z0-9_-]", "_");
        return combined ? "Receipt_Combined_" + safe + ".docx" : "Receipt_" + safe + ".docx";
    }

    private static void writeDocument(XWPFDocument doc, ReceiptLetterView view) {
        applyDocumentDefaults(doc);
        // Blank lines first so the user can place the cursor above the receipt and adjust content.
        addEditableSpacer(doc);
        addEditableSpacer(doc);
        writeHeader(doc, view);
        addBlankLine(doc);
        writeNarrative(doc, view);
        addBlankLine(doc);
        writeAmountAndSignatory(doc, view);
        if (view.showChequeRealizationDisclaimer()) {
            addBlankLine(doc);
            XWPFParagraph disclaimer = doc.createParagraph();
            disclaimer.setAlignment(ParagraphAlignment.LEFT);
            applyLineSpacing(disclaimer);
            appendText(
                    disclaimer,
                    "This receipt is issued subject to realization of the Cheque, if bounced, it stands automatically cancelled.",
                    false,
                    10);
        }
    }

    private static void writeHeader(XWPFDocument doc, ReceiptLetterView view) {
        XWPFTable table = doc.createTable(1, 2);
        setTableFullWidth(table);
        removeTableBorders(table);

        XWPFTableRow row = table.getRow(0);
        writeHeaderCell(
                row.getCell(0),
                "Receipt No.: ",
                nullToDash(view.receiptNumberPrint()),
                ParagraphAlignment.LEFT);
        writeHeaderCell(
                row.getCell(1),
                "Date:- ",
                nullToDash(view.receiptDateShort()),
                ParagraphAlignment.RIGHT);
    }

    private static void writeHeaderCell(
            XWPFTableCell cell, String label, String value, ParagraphAlignment alignment) {
        cell.removeParagraph(0);
        setCellPadding(cell, 40, 80, 40, 40);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(alignment);
        p.setSpacingBefore(40);
        p.setSpacingAfter(80);
        applyLineSpacing(p);
        // Avoid italic here — Bookman italic + underline clipping cuts letters like "Date".
        appendText(p, label, true, 12, false);
        appendText(p, value, true, 12, false);
    }

    private static void writeNarrative(XWPFDocument doc, ReceiptLetterView view) {
        XWPFParagraph narrative = doc.createParagraph();
        narrative.setAlignment(ParagraphAlignment.BOTH);
        applyLineSpacing(narrative);
        appendText(narrative, "Received with thanks from ", false, 12);
        appendText(narrative, view.payerNamesPrint(), true, 12);
        appendText(narrative, " a sum of ", false, 12);
        appendText(narrative, view.amountFiguresPrint(), true, 12);
        appendText(narrative, " (", false, 12);
        appendText(narrative, view.amountWordsPrint(), true, 12);
        appendText(narrative, ") vide ", false, 12);
        appendInstrumentNarrative(narrative, view.instrumentNarrativePrint());
        appendText(narrative, ", Payment towards ", false, 12);
        appendText(narrative, view.paymentPurposePrint(), true, 12);
        appendText(narrative, " against ", false, 12);
        appendText(narrative, view.flatNumberPrint(), true, 12);
        appendText(narrative, " on ", false, 12);
        appendText(narrative, view.floorPhrasePrint(), true, 12);
        appendText(narrative, " in the Project Known as ", false, 12);
        appendText(narrative, view.projectNamePrint(), true, 12);
        appendText(narrative, " situated at ", false, 12);
        appendText(narrative, view.siteAddressPrint(), true, 12);
    }

    /**
     * Keep values bold (mode/ref, date, bank) but leave connector words "dated" / "drawn on" normal.
     */
    private static void appendInstrumentNarrative(XWPFParagraph paragraph, String instrument) {
        String text = instrument != null ? instrument.trim() : "";
        if (text.isEmpty() || "—".equals(text)) {
            appendText(paragraph, "—", true, 12);
            return;
        }
        // Support combined receipts joined with "; "
        String[] parts = text.split(";\\s*");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                appendText(paragraph, "; ", false, 12);
            }
            appendSingleInstrument(paragraph, parts[i].trim());
        }
    }

    private static void appendSingleInstrument(XWPFParagraph paragraph, String part) {
        if (part.isEmpty()) {
            return;
        }
        String remainder = part;
        String bank = null;
        int drawnIdx = indexOfIgnoreCase(remainder, ", drawn on ");
        if (drawnIdx >= 0) {
            bank = remainder.substring(drawnIdx + ", drawn on ".length()).trim();
            remainder = remainder.substring(0, drawnIdx);
        }
        int datedIdx = indexOfIgnoreCase(remainder, " dated ");
        if (datedIdx >= 0) {
            String beforeDated = remainder.substring(0, datedIdx).trim();
            String datePart = remainder.substring(datedIdx + " dated ".length()).trim();
            if (!beforeDated.isEmpty()) {
                appendText(paragraph, beforeDated, true, 12);
            }
            appendText(paragraph, " dated ", false, 12);
            if (!datePart.isEmpty()) {
                appendText(paragraph, datePart, true, 12);
            }
        } else {
            appendText(paragraph, remainder, true, 12);
        }
        if (bank != null && !bank.isEmpty()) {
            appendText(paragraph, ", drawn on ", false, 12);
            appendText(paragraph, bank, true, 12);
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    private static void writeAmountAndSignatory(XWPFDocument doc, ReceiptLetterView view) {
        XWPFTable table = doc.createTable(2, 2);
        setTableFullWidth(table);
        removeTableBorders(table);
        setColumnWidth(table, 0, 3600);
        setColumnWidth(table, 1, 5400);

        XWPFTableRow topRow = table.getRow(0);
        topRow.setHeight(700);
        writeCompactAmountBox(topRow.getCell(0), view.amountFiguresPrint());

        XWPFTableCell forCell = topRow.getCell(1);
        forCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        forCell.removeParagraph(0);
        XWPFParagraph forPara = forCell.addParagraph();
        forPara.setAlignment(ParagraphAlignment.RIGHT);
        forPara.setSpacingAfter(0);
        applyLineSpacing(forPara);
        appendText(forPara, "For ", false, 12, true);
        appendText(forPara, view.builderCompanyPrint(), true, 12, true);

        XWPFTableRow bottomRow = table.getRow(1);
        bottomRow.getCell(0).removeParagraph(0);

        XWPFTableCell signatoryCell = bottomRow.getCell(1);
        signatoryCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.BOTTOM);
        signatoryCell.removeParagraph(0);
        XWPFParagraph spacePara = signatoryCell.addParagraph();
        spacePara.setAlignment(ParagraphAlignment.RIGHT);
        spacePara.setSpacingBefore(500);
        spacePara.setSpacingAfter(0);
        applyLineSpacing(spacePara);

        XWPFParagraph labelPara = signatoryCell.addParagraph();
        labelPara.setAlignment(ParagraphAlignment.RIGHT);
        labelPara.setSpacingBefore(0);
        applyLineSpacing(labelPara);
        appendText(labelPara, "Authorised Signatory", true, 12, true);
    }

    private static void writeCompactAmountBox(XWPFTableCell slotCell, String amountFigures) {
        slotCell.removeParagraph(0);
        slotCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        setCellWidth(slotCell, 3400);
        clearCellBorders(slotCell);
        setCellPadding(slotCell, 80, 80, 40, 40);

        XWPFParagraph para = slotCell.addParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        if (!insertRoundedAmountBox(para, nullToDash(amountFigures))) {
            // Fallback if VML round-rect cannot be built
            applyLineSpacing(para);
            para.setAlignment(ParagraphAlignment.CENTER);
            appendAmountText(para, nullToDash(amountFigures));
            applyAmountBoxBorder(slotCell);
        }
    }

    /**
     * Word table cells cannot have rounded corners; use a VML round-rect text box so the
     * amount sits vertically centered with padding inside a rounded border.
     */
    private static boolean insertRoundedAmountBox(XWPFParagraph paragraph, String amount) {
        String safe = escapeXml(amount);
        String xml =
                "<w:r xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                        + "xmlns:v=\"urn:schemas-microsoft-com:vml\" "
                        + "xmlns:o=\"urn:schemas-microsoft-com:office:office\">"
                        + "<w:pict>"
                        + "<v:roundrect arcsize=\"0.28\" fillcolor=\"white\" stroked=\"t\" "
                        + "strokeweight=\"1pt\" "
                        + "style=\"width:168pt;height:36pt;mso-wrap-style:none;v-text-anchor:middle\">"
                        + "<v:stroke joinstyle=\"round\"/>"
                        + "<v:textbox inset=\"10pt,7pt,10pt,7pt\">"
                        + "<w:txbxContent>"
                        + "<w:p>"
                        + "<w:pPr>"
                        + "<w:jc w:val=\"center\"/>"
                        + "<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/>"
                        + "</w:pPr>"
                        + "<w:r>"
                        + "<w:rPr>"
                        + "<w:b/>"
                        + "<w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>"
                        + "<w:rFonts w:ascii=\""
                        + FONT
                        + "\" w:hAnsi=\""
                        + FONT
                        + "\" w:cs=\""
                        + FONT
                        + "\"/>"
                        + "</w:rPr>"
                        + "<w:t xml:space=\"preserve\">"
                        + safe
                        + "</w:t>"
                        + "</w:r>"
                        + "</w:p>"
                        + "</w:txbxContent>"
                        + "</v:textbox>"
                        + "</v:roundrect>"
                        + "</w:pict>"
                        + "</w:r>";
        try {
            CTR run = CTR.Factory.parse(xml);
            paragraph.getCTP().addNewR().set(run);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void appendText(XWPFParagraph paragraph, String text, boolean bold, int fontSize) {
        appendText(paragraph, text, bold, fontSize, false);
    }

    private static void appendText(
            XWPFParagraph paragraph, String text, boolean bold, int fontSize, boolean italic) {
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "—");
        run.setBold(bold);
        run.setItalic(italic);
        run.setFontSize(fontSize);
        applyRunFont(run, FONT);
    }

    private static void appendAmountText(XWPFParagraph paragraph, String text) {
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "—");
        run.setBold(true);
        run.setFontSize(14);
        applyRunFont(run, FONT);
    }

    private static void applyDocumentDefaults(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        CTFonts fonts = CTFonts.Factory.newInstance();
        fonts.setAscii(FONT);
        fonts.setHAnsi(FONT);
        fonts.setCs(FONT);
        fonts.setEastAsia(FONT);
        styles.setDefaultFonts(fonts);
        applyPageMargins(doc);
    }

    private static void applyPageMargins(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(PAGE_MARGIN_TOP);
        pgMar.setBottom(PAGE_MARGIN_BOTTOM);
        pgMar.setLeft(PAGE_MARGIN_SIDE);
        pgMar.setRight(PAGE_MARGIN_SIDE);
    }

    private static void applyLineSpacing(XWPFParagraph paragraph) {
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLine(LINE_SPACING_15);
        spacing.setLineRule(STLineSpacingRule.AUTO);
    }

    private static void applyRunFont(XWPFRun run, String fontFamily) {
        run.setFontFamily(fontFamily);
        CTRPr rPr = run.getCTR().getRPr();
        if (rPr == null) {
            rPr = run.getCTR().addNewRPr();
        }
        applyRPrFont(rPr, fontFamily);
    }

    private static void applyRPrFont(CTRPr rPr, String fontFamily) {
        CTFonts fonts =
                rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
        fonts.setAscii(fontFamily);
        fonts.setHAnsi(fontFamily);
        fonts.setCs(fontFamily);
        fonts.setEastAsia(fontFamily);
    }

    private static void addBlankLine(XWPFDocument doc) {
        XWPFParagraph blank = doc.createParagraph();
        applyLineSpacing(blank);
    }

    /** Empty paragraph the user can click into above the receipt body. */
    private static void addEditableSpacer(XWPFDocument doc) {
        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingBefore(0);
        spacer.setSpacingAfter(120);
        applyLineSpacing(spacer);
        XWPFRun run = spacer.createRun();
        run.setText("");
        run.setFontSize(12);
        applyRunFont(run, FONT);
    }

    private static void setTableFullWidth(XWPFTable table) {
        CTTblPr tblPr = ensureTblPr(table);
        CTTblWidth width = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000));
    }

    private static CTTblPr ensureTblPr(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        return tblPr != null ? tblPr : table.getCTTbl().addNewTblPr();
    }

    private static void removeTableBorders(XWPFTable table) {
        CTTblPr tblPr = ensureTblPr(table);
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewBottom().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);
    }

    private static void applyAmountBoxBorder(XWPFTableCell cell) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setCellBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setCellBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setCellBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setCellBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setCellPadding(cell, 120, 120, 160, 160);
    }

    private static void clearCellBorders(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setNoneBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setNoneBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setNoneBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setNoneBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
    }

    private static void setCellPadding(XWPFTableCell cell, int top, int bottom, int left, int right) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        setCellMargin(mar.isSetTop() ? mar.getTop() : mar.addNewTop(), top);
        setCellMargin(mar.isSetBottom() ? mar.getBottom() : mar.addNewBottom(), bottom);
        setCellMargin(mar.isSetLeft() ? mar.getLeft() : mar.addNewLeft(), left);
        setCellMargin(mar.isSetRight() ? mar.getRight() : mar.addNewRight(), right);
    }

    private static void setNoneBorder(CTBorder border) {
        border.setVal(STBorder.NONE);
        border.setSz(BigInteger.ZERO);
        border.setColor("auto");
    }

    private static void setCellBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(6));
        border.setColor("000000");
    }

    private static void setCellMargin(CTTblWidth mar, int twips) {
        mar.setType(STTblWidth.DXA);
        mar.setW(BigInteger.valueOf(twips));
    }

    private static void setCellWidth(XWPFTableCell cell, int widthTwips) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth width = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(widthTwips));
    }

    private static void setColumnWidth(XWPFTable table, int columnIndex, int widthTwips) {
        for (XWPFTableRow row : table.getRows()) {
            if (columnIndex >= row.getTableCells().size()) {
                continue;
            }
            XWPFTableCell cell = row.getCell(columnIndex);
            setCellWidth(cell, widthTwips);
        }
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
