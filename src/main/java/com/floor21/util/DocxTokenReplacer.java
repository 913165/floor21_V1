package com.floor21.util;

import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/** Simple token replacement across Word paragraphs and table cells. */
public final class DocxTokenReplacer {

    private DocxTokenReplacer() {}

    public static void replaceAll(XWPFDocument doc, Map<String, String> tokens) {
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                replaceInParagraph(paragraph, tokens);
            } else if (element instanceof XWPFTable table) {
                replaceInTable(table, tokens);
            }
        }
    }

    private static void replaceInTable(XWPFTable table, Map<String, String> tokens) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    replaceInParagraph(paragraph, tokens);
                }
            }
        }
    }

    private static void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> tokens) {
        String full = paragraph.getText();
        if (full == null || full.isBlank()) {
            return;
        }
        String replaced = full;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                replaced = replaced.replace(entry.getKey(), entry.getValue());
            }
        }
        if (replaced.equals(full)) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            XWPFRun run = paragraph.createRun();
            run.setText(replaced);
            return;
        }
        XWPFRun first = runs.get(0);
        first.setText(replaced, 0);
        for (int i = runs.size() - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
    }
}
