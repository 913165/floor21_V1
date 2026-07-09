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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptWordExportService {

    private static final String FONT = "Bookman Old Style";

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
        writeHeader(doc, view);
        addBlankLine(doc);
        writeNarrative(doc, view);
        addBlankLine(doc);
        writeAmountAndSignatory(doc, view);
        if (view.showChequeRealizationDisclaimer()) {
            addBlankLine(doc);
            XWPFParagraph disclaimer = doc.createParagraph();
            disclaimer.setAlignment(ParagraphAlignment.LEFT);
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
        setCellText(
                row.getCell(0),
                "Receipt No.: " + nullToDash(view.receiptNumberPrint()),
                true,
                12,
                ParagraphAlignment.LEFT,
                true);
        setCellText(
                row.getCell(1),
                "Date:- " + nullToDash(view.receiptDateShort()),
                true,
                12,
                ParagraphAlignment.RIGHT,
                true);
    }

    private static void writeNarrative(XWPFDocument doc, ReceiptLetterView view) {
        XWPFParagraph narrative = doc.createParagraph();
        narrative.setAlignment(ParagraphAlignment.BOTH);
        appendText(narrative, "Received with thanks from ", false, 12);
        appendText(narrative, view.payerNamesPrint(), true, 12);
        appendText(narrative, " a sum of ", false, 12);
        appendText(narrative, view.amountFiguresPrint(), true, 12);
        appendText(narrative, " (", false, 12);
        appendText(narrative, view.amountWordsPrint(), true, 12);
        appendText(narrative, ") vide ", false, 12);
        appendText(narrative, view.instrumentNarrativePrint(), true, 12);
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

    private static void writeAmountAndSignatory(XWPFDocument doc, ReceiptLetterView view) {
        XWPFTable table = doc.createTable(2, 2);
        setTableFullWidth(table);
        removeTableBorders(table);
        setColumnWidth(table, 0, 3600);
        setColumnWidth(table, 1, 5400);

        XWPFTableRow topRow = table.getRow(0);
        writeCompactAmountBox(topRow.getCell(0), view.amountFiguresPrint());

        XWPFTableCell forCell = topRow.getCell(1);
        forCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        forCell.removeParagraph(0);
        XWPFParagraph forPara = forCell.addParagraph();
        forPara.setAlignment(ParagraphAlignment.RIGHT);
        forPara.setSpacingAfter(0);
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

        XWPFParagraph labelPara = signatoryCell.addParagraph();
        labelPara.setAlignment(ParagraphAlignment.RIGHT);
        labelPara.setSpacingBefore(0);
        appendText(labelPara, "Authorised Signatory", true, 12, true);
    }

    private static void writeCompactAmountBox(XWPFTableCell slotCell, String amountFigures) {
        slotCell.removeParagraph(0);
        slotCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        setCellWidth(slotCell, 3200);

        XWPFParagraph para = slotCell.addParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setSpacingBefore(0);
        para.setSpacingAfter(0);
        appendAmountText(para, nullToDash(amountFigures));
        applyAmountBoxBorder(slotCell);
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
        doc.createParagraph();
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
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setCellBorder(borders.addNewTop());
        setCellBorder(borders.addNewBottom());
        setCellBorder(borders.addNewLeft());
        setCellBorder(borders.addNewRight());
        CTTcMar mar = tcPr.isSetTcMar() ? tcPr.getTcMar() : tcPr.addNewTcMar();
        setCellMargin(mar.addNewTop(), 8);
        setCellMargin(mar.addNewBottom(), 8);
        setCellMargin(mar.addNewLeft(), 140);
        setCellMargin(mar.addNewRight(), 220);
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

    private static void setCellText(
            XWPFTableCell cell,
            String text,
            boolean bold,
            int fontSize,
            ParagraphAlignment alignment,
            boolean italic) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(alignment);
        appendText(p, text, bold, fontSize, italic);
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
