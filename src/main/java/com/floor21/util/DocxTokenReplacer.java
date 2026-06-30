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
                replaceInCell(cell, tokens);
            }
        }
    }

    /** Replace across all paragraphs in a cell (sample template splits address / amounts). */
    private static void replaceInCell(XWPFTableCell cell, Map<String, String> tokens) {
        String full = cell.getText();
        if (full == null || full.isBlank()) {
            return;
        }
        String replaced = applyTokens(full, tokens);
        if (replaced.equals(full)) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                replaceInParagraph(paragraph, tokens);
            }
            return;
        }
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        if (paragraphs.isEmpty()) {
            cell.addParagraph().createRun().setText(replaced);
            return;
        }
        XWPFParagraph first = paragraphs.get(0);
        List<XWPFRun> runs = first.getRuns();
        if (runs.isEmpty()) {
            first.createRun().setText(replaced);
        } else {
            runs.get(0).setText(replaced, 0);
            for (int i = runs.size() - 1; i >= 1; i--) {
                first.removeRun(i);
            }
        }
        for (int i = paragraphs.size() - 1; i >= 1; i--) {
            cell.removeParagraph(i);
        }
    }

    private static void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> tokens) {
        String full = paragraph.getText();
        if (full == null || full.isBlank()) {
            return;
        }
        String replaced = applyTokens(full, tokens);
        if (replaced.equals(full)) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            paragraph.createRun().setText(replaced);
            return;
        }
        runs.get(0).setText(replaced, 0);
        for (int i = runs.size() - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
    }

    private static String applyTokens(String text, Map<String, String> tokens) {
        String replaced = text;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                replaced = replaced.replace(entry.getKey(), entry.getValue());
            }
        }
        return replaced;
    }
}
