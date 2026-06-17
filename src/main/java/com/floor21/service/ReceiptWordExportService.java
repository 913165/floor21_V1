package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.entity.Receipt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptWordExportService {

    private final ReceiptPrintService receiptPrintService;

    @Transactional(readOnly = true)
    public byte[] generate(Receipt receipt, boolean allOwners) {
        ReceiptLetterView view = receiptPrintService.buildLetterView(receipt, allOwners);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocument(doc, view);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate receipt Word document", ex);
        }
    }

    public String suggestedFilename(Receipt receipt) {
        String recNo =
                receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()
                        ? receipt.getReceiptNumber().trim()
                        : receipt.getId().toString();
        String safe = recNo.replaceAll("[^a-zA-Z0-9_-]", "_");
        return "Receipt_" + safe + ".docx";
    }

    private static void writeDocument(XWPFDocument doc, ReceiptLetterView view) {
        XWPFTable header = doc.createTable(1, 2);
        setTableFullWidth(header);
        setCellText(header.getRow(0).getCell(0), "Receipt No.: " + nullToDash(view.receiptNumber()), false, 12);
        setCellText(
                header.getRow(0).getCell(1),
                "Date:- " + nullToDash(view.receiptDateFormatted()),
                false,
                12,
                ParagraphAlignment.RIGHT);
        removeTableBorders(header);

        addBlankLine(doc);

        XWPFParagraph narrative = doc.createParagraph();
        narrative.setAlignment(ParagraphAlignment.BOTH);
        appendText(narrative, "Received with thanks from ", false, 12);
        appendText(narrative, view.payerNamesPrint(), true, 12);
        appendText(narrative, " a sum of ", false, 12);
        appendText(narrative, view.amountFiguresPrint(), true, 12);
        appendText(narrative, " (", false, 12);
        appendText(narrative, view.amountWordsPrint(), true, 12);
        appendText(narrative, ") vide ", false, 12);
        appendText(narrative, view.paymentInstrumentPrint(), true, 12);
        appendText(narrative, " dated ", false, 12);
        appendText(narrative, view.instrumentDateFormatted(), true, 12);
        appendText(narrative, ", drawn on ", false, 12);
        appendText(narrative, view.drawnOnBankPrint(), true, 12);
        appendText(narrative, ", Payment towards ", false, 12);
        appendText(narrative, view.purposeNarrativePrint(), true, 12);
        appendText(narrative, " against Flat No.", false, 12);
        appendText(narrative, view.flatNumberPrint(), true, 12);
        appendText(narrative, " on ", false, 12);
        appendText(narrative, view.floorPhrasePrint(), true, 12);
        appendText(narrative, " in the Project Known as ", false, 12);
        appendText(narrative, view.projectNamePrint(), true, 12);
        appendText(narrative, " situated at ", false, 12);
        appendText(narrative, view.siteAddressPrint(), true, 12);
        appendText(narrative, ".", false, 12);

        addBlankLine(doc);
        addBlankLine(doc);

        XWPFTable footer = doc.createTable(1, 2);
        setTableFullWidth(footer);
        setCellText(footer.getRow(0).getCell(0), view.amountFiguresPrint(), true, 12);
        setSignatoryCell(footer.getRow(0).getCell(1), view.builderCompanyPrint());
        removeTableBorders(footer);

        addBlankLine(doc);
        addBlankLine(doc);

        String disclaimer =
                view.showChequeRealizationDisclaimer()
                        ? "This receipt is issued subject to realization of the Cheque, if bounced, it stands automatically cancelled."
                        : "This receipt is issued on record against the particulars stated herein.";
        addParagraph(doc, disclaimer, false, 10);
    }

    private static void appendText(XWPFParagraph paragraph, String text, boolean bold, int fontSize) {
        XWPFRun run = paragraph.createRun();
        run.setText(text != null ? text : "—");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily("Times New Roman");
    }

    private static void addParagraph(XWPFDocument doc, String text, boolean bold, int fontSize) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setFontFamily("Times New Roman");
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
        table.removeBorders();
    }

    private static void setSignatoryCell(XWPFTableCell cell, String companyName) {
        cell.removeParagraph(0);

        XWPFParagraph forLine = cell.addParagraph();
        forLine.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun forLabel = forLine.createRun();
        forLabel.setText("For ");
        forLabel.setFontSize(12);
        forLabel.setFontFamily("Times New Roman");
        XWPFRun company = forLine.createRun();
        company.setText(nullToDash(companyName));
        company.setBold(true);
        company.setFontSize(12);
        company.setFontFamily("Times New Roman");

        XWPFParagraph spacer = cell.addParagraph();
        spacer.setAlignment(ParagraphAlignment.RIGHT);
        spacer.setSpacingBefore(120);

        XWPFParagraph sigLine = cell.addParagraph();
        sigLine.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun sig = sigLine.createRun();
        sig.setText("Authorised Signatory");
        sig.setFontSize(11);
        sig.setFontFamily("Times New Roman");
    }

    private static void setCellText(
            XWPFTableCell cell, String text, boolean bold, int fontSize) {
        setCellText(cell, text, bold, fontSize, ParagraphAlignment.LEFT);
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
        run.setFontFamily("Times New Roman");
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }
}
