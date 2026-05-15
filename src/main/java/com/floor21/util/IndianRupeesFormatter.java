package com.floor21.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian-style rupee figures (e.g. 1,50,000/-) and amount in words for receipts. */
public final class IndianRupeesFormatter {

    private static final String[] UNITS = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private IndianRupeesFormatter() {}

    /** e.g. {@code Rs. 1,50,000/-} or {@code Rs. 1,234.56/-} when fractional paise matter. */
    public static String formatFigures(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = scaled.longValue();
        int paise =
                scaled
                        .subtract(BigDecimal.valueOf(rupees))
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValue();
        String intPart = indianCommaDigits(String.valueOf(Math.abs(rupees)));
        if (rupees < 0) {
            intPart = "-" + intPart;
        }
        if (paise > 0) {
            return "Rs. " + intPart + "." + String.format("%02d", paise) + "/-";
        }
        return "Rs. " + intPart + "/-";
    }

    /** Full legal-style clause, e.g. {@code Rupees One Lakh Fifty Thousand Only}. */
    public static String formatWordsOnly(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = scaled.longValue();
        int paise =
                scaled
                        .subtract(BigDecimal.valueOf(rupees))
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValue();
        StringBuilder sb = new StringBuilder("Rupees ");
        sb.append(convertIndianRupees(rupees));
        if (paise > 0) {
            sb.append(" and ").append(belowHundred(paise)).append(" Paise");
        }
        sb.append(" Only");
        return sb.toString();
    }

    private static String indianCommaDigits(String digits) {
        if (digits.length() <= 3) {
            return digits.isEmpty() ? "0" : digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        int rlen = rest.length();
        int first = rlen % 2 == 0 ? 2 : 1;
        StringBuilder sb = new StringBuilder();
        sb.append(rest, 0, first);
        for (int p = first; p < rlen; p += 2) {
            sb.append(',').append(rest, p, p + 2);
        }
        sb.append(',').append(last3);
        return sb.toString();
    }

    private static String convertIndianRupees(long rupees) {
        if (rupees == 0) {
            return "Zero";
        }
        boolean neg = rupees < 0;
        long n = Math.abs(rupees);
        if (n > 999_999_999_999L) {
            return "Amount too large to spell";
        }
        int crore = (int) (n / 10_000_000L);
        n %= 10_000_000L;
        int lakh = (int) (n / 100_000L);
        n %= 100_000L;
        int thousand = (int) (n / 1000L);
        int last = (int) (n % 1000L);
        StringBuilder sb = new StringBuilder();
        if (crore > 0) {
            sb.append(belowThousand(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(belowThousand(lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(belowThousand(thousand)).append(" Thousand ");
        }
        if (last > 0) {
            sb.append(belowThousand(last));
        }
        String s = sb.toString().trim();
        return neg ? "Negative " + s : s;
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return UNITS[n];
        }
        int t = n / 10;
        int u = n % 10;
        if (u == 0) {
            return TENS[t];
        }
        return TENS[t] + " " + UNITS[u];
    }

    private static String belowThousand(int n) {
        if (n < 100) {
            return belowHundred(n);
        }
        int h = n / 100;
        int rem = n % 100;
        if (rem == 0) {
            return UNITS[h] + " Hundred";
        }
        return UNITS[h] + " Hundred " + belowHundred(rem);
    }
}
