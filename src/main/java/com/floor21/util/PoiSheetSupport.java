package com.floor21.util;

import org.apache.poi.ss.usermodel.Sheet;

/** POI helpers that behave on headless Linux (no AWT fonts for auto-size). */
public final class PoiSheetSupport {

    private static final int DEFAULT_FALLBACK_WIDTH = 5120;

    private PoiSheetSupport() {}

    public static void autoSizeColumns(Sheet sheet, int columnCount) {
        autoSizeColumns(sheet, columnCount, DEFAULT_FALLBACK_WIDTH);
    }

    /**
     * Tries POI auto-size; on headless servers without font metrics, uses fixed widths instead.
     */
    public static void autoSizeColumns(Sheet sheet, int columnCount, int fallbackWidth) {
        for (int c = 0; c < columnCount; c++) {
            autoSizeColumn(sheet, c, fallbackWidth);
        }
    }

    public static void autoSizeColumn(Sheet sheet, int columnIndex, int fallbackWidth) {
        try {
            sheet.autoSizeColumn(columnIndex);
            int width = sheet.getColumnWidth(columnIndex);
            if (width > 15000) {
                sheet.setColumnWidth(columnIndex, 15000);
            }
        } catch (Exception ex) {
            sheet.setColumnWidth(columnIndex, fallbackWidth);
        }
    }
}
