package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.entity.Receipt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptWordExportService {

    private static final String FONT = "Times New Roman";

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
        writeFooter(doc, view);
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
        XWPFTable table = doc.createTable(1, 2);
        setTableFullWidth(table);
        removeTableBorders(table);

        XWPFTableRow row = table.getRow(0);
        XWPFTableCell amountCell = row.getCell(0);
        amountCell.removeParagraph(0);
        XWPFParagraph amountPara = amountCell.addParagraph();
        amountPara.setAlignment(ParagraphAlignment.LEFT);
        appendText(amountPara, nullToDash(view.amountFiguresPrint()), true, 13);
        applyCellBorder(amountCell);

        XWPFTableCell signatoryCell = row.getCell(1);
        signatoryCell.removeParagraph(0);
        XWPFParagraph forPara = signatoryCell.addParagraph();
        forPara.setAlignment(ParagraphAlignment.RIGHT);
        appendText(forPara, "For ", false, 12, true);
        appendText(forPara, view.builderCompanyPrint(), true, 12, true);

        XWPFParagraph spacePara = signatoryCell.addParagraph();
        spacePara.setAlignment(ParagraphAlignment.RIGHT);
        appendText(spacePara, " ", false, 12);
        spacePara.setSpacingAfter(800);

        XWPFParagraph labelPara = signatoryCell.addParagraph();
        labelPara.setAlignment(ParagraphAlignment.RIGHT);
        appendText(labelPara, "Authorised Signatory", true, 12, true);
    }

    private static void writeFooter(XWPFDocument doc, ReceiptLetterView view) {
        addBlankLine(doc);
        addBlankLine(doc);
        if (hasText(view.footerAddressPrint())) {
            XWPFParagraph address = doc.createParagraph();
            address.setAlignment(ParagraphAlignment.CENTER);
            appendText(address, nullToDash(view.footerAddressPrint()), false, 10);
        }
        if (hasText(view.footerPhonePrint()) || hasText(view.footerEmailPrint())) {
            XWPFParagraph contact = doc.createParagraph();
            contact.setAlignment(ParagraphAlignment.CENTER);
            if (hasText(view.footerPhonePrint())) {
                appendText(contact, nullToDash(view.footerPhonePrint()), false, 10);
            }
            if (hasText(view.footerPhonePrint()) && hasText(view.footerEmailPrint())) {
                appendText(contact, "   ", false, 10);
            }
            if (hasText(view.footerEmailPrint())) {
                appendText(contact, nullToDash(view.footerEmailPrint()), false, 10);
            }
        }
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
        run.setFontFamily(FONT);
    }

    private static void addBlankLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static void setTableFullWidth(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(java.math.BigInteger.valueOf(5000));
    }

    private static void removeTableBorders(XWPFTable table) {
        CTTblBorders borders = table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewBottom().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);
    }

    private static void applyCellBorder(XWPFTableCell cell) {
        cell.setVerticalAlignment(org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank() && !"—".equals(value.trim());
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
