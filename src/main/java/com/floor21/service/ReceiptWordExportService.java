package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.dto.ReceiptPaymentTableRow;
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
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("RECEIPT");
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setFontFamily(FONT);
        titleRun.setCharacterSpacing(40);

        addBlankLine(doc);

        XWPFParagraph narrative = doc.createParagraph();
        narrative.setAlignment(ParagraphAlignment.BOTH);
        appendText(narrative, "Received from ", false, 12);
        appendText(narrative, view.payerNamesPrint(), true, 12);
        appendText(narrative, " as on ", false, 12);
        appendText(narrative, view.receiptDateOrdinal(), true, 12);
        appendText(narrative, " a sum of ", false, 12);
        appendText(narrative, view.amountFiguresPrint(), true, 12);
        appendText(narrative, " (", false, 12);
        appendText(narrative, view.amountWordsPrint(), true, 12);
        appendText(narrative, ") as and by way of ", false, 12);
        appendText(narrative, view.paymentWayPrint(), true, 12);
        appendText(narrative, " out of the Total agreed consideration of ", false, 12);
        appendText(narrative, view.totalConsiderationFiguresPrint(), true, 12);
        appendText(narrative, " (", false, 12);
        appendText(narrative, view.totalConsiderationWordsPrint(), true, 12);
        appendText(narrative, ") in respect of the purchase of unit being ", false, 12);
        appendText(narrative, view.unitDescriptionPrint(), true, 12);
        appendText(narrative, " in the said Project known as \"", false, 12);
        appendText(narrative, view.projectNamePrint(), true, 12);
        appendText(narrative, "\".", false, 12);

        addBlankLine(doc);

        XWPFParagraph land = doc.createParagraph();
        land.setAlignment(ParagraphAlignment.BOTH);
        appendText(land, view.landAddressPrint(), true, 12);
        appendText(land, ".", false, 12);

        addBlankLine(doc);

        writePaymentTable(doc, view);

        addBlankLine(doc);

        addParagraph(doc, "WE SAY RECEIVED", true, 12);
        addBlankLine(doc);
        addParagraph(doc, "In presence of:", false, 12);
        addParagraph(doc, "1. ___________________________", false, 12);
        addParagraph(doc, "2. ___________________________", false, 12);
        addBlankLine(doc);
        addParagraph(doc, "Date: _______________________", false, 12);
        addParagraph(doc, "Place: " + nullToDash(view.placePrint()), false, 12);

        if (view.showChequeRealizationDisclaimer()) {
            addBlankLine(doc);
            addParagraph(
                    doc,
                    "This receipt is issued subject to realization of the Cheque; if bounced, it stands automatically cancelled.",
                    false,
                    10);
        }
    }

    private static void writePaymentTable(XWPFDocument doc, ReceiptLetterView view) {
        List<ReceiptPaymentTableRow> rows = view.paymentTableRows();
        int dataRows = rows != null && !rows.isEmpty() ? rows.size() : 1;
        XWPFTable table = doc.createTable(1 + dataRows + 1, 4);
        setTableFullWidth(table);
        applyTableBorders(table);

        String[] headers = {"Sr.No", "Date", "Cheque No./ UTR detail", "Amount (Rs.)"};
        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCellText(headerRow.getCell(i), headers[i], true, 11, ParagraphAlignment.CENTER);
        }

        if (rows == null || rows.isEmpty()) {
            XWPFTableRow dataRow = table.getRow(1);
            setCellText(dataRow.getCell(0), "1", false, 11, ParagraphAlignment.CENTER);
            setCellText(dataRow.getCell(1), "—", false, 11, ParagraphAlignment.CENTER);
            setCellText(dataRow.getCell(2), "—", false, 11, ParagraphAlignment.LEFT);
            setCellText(dataRow.getCell(3), "—", false, 11, ParagraphAlignment.RIGHT);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                ReceiptPaymentTableRow row = rows.get(i);
                XWPFTableRow dataRow = table.getRow(i + 1);
                setCellText(
                        dataRow.getCell(0),
                        String.valueOf(row.serialNo()),
                        false,
                        11,
                        ParagraphAlignment.CENTER);
                setCellText(
                        dataRow.getCell(1),
                        nullToEmpty(row.dateFormatted()),
                        false,
                        11,
                        ParagraphAlignment.CENTER);
                setCellText(
                        dataRow.getCell(2),
                        nullToDash(row.instrumentDetail()),
                        false,
                        11,
                        ParagraphAlignment.LEFT);
                setCellText(
                        dataRow.getCell(3),
                        nullToDash(row.amountDisplay()),
                        false,
                        11,
                        ParagraphAlignment.RIGHT);
            }
        }

        XWPFTableRow totalRow = table.getRow(dataRows + 1);
        setCellText(totalRow.getCell(0), "", false, 11, ParagraphAlignment.LEFT);
        setCellText(totalRow.getCell(1), "", false, 11, ParagraphAlignment.LEFT);
        setCellText(totalRow.getCell(2), "Total", true, 11, ParagraphAlignment.RIGHT);
        setCellText(totalRow.getCell(3), nullToDash(view.amountFiguresPrint()), true, 11, ParagraphAlignment.RIGHT);

        XWPFTableCell totalCell = totalRow.getCell(3);
        XWPFParagraph wordsPara = totalCell.addParagraph();
        wordsPara.setAlignment(ParagraphAlignment.RIGHT);
        appendText(wordsPara, "(" + nullToDash(view.amountWordsPrint()) + ")", false, 10);
    }

    private static void appendText(XWPFParagraph paragraph, String text, boolean bold, int fontSize) {
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "—");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily(FONT);
    }

    private static void addParagraph(XWPFDocument doc, String text, boolean bold, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily(FONT);
    }

    private static void addBlankLine(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static void setTableFullWidth(XWPFTable table) {
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.PCT);
        width.setW(BigInteger.valueOf(5000));
    }

    private static void applyTableBorders(XWPFTable table) {
        CTTblBorders borders = table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);
    }

    private static void setCellText(
            XWPFTableCell cell, String text, boolean bold, int fontSize, ParagraphAlignment alignment) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(alignment);
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily(FONT);
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
