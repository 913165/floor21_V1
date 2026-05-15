package com.floor21.service;

import com.floor21.entity.Bank;
import com.floor21.entity.Building;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Receipt;
import com.floor21.util.IndianRupeesFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
public class ReceiptPrintService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter PRINT_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    public void addPrintAttributes(Model model, Receipt receipt) {
        Flat flat = receipt.getBooking().getFlat();
        Building building = flat.getBuilding();
        Client client = receipt.getBooking().getClient();

        model.addAttribute("receiptDateFormatted", formatDate(receipt.getReceiptDate()));
        model.addAttribute(
                "instrumentDateFormatted",
                formatDate(receipt.getChequeDate() != null ? receipt.getChequeDate() : receipt.getReceiptDate()));
        model.addAttribute("amountFiguresPrint", IndianRupeesFormatter.formatFigures(receipt.getAmount()));
        model.addAttribute("amountWordsPrint", IndianRupeesFormatter.formatWordsOnly(receipt.getAmount()));
        model.addAttribute("payerNamesPrint", resolvePayerNames(client));
        model.addAttribute("paymentInstrumentPrint", buildPaymentInstrument(receipt));
        model.addAttribute("drawnOnBankPrint", resolveDrawnOn(receipt));
        model.addAttribute("purposeNarrativePrint", buildPurposeNarrative(receipt));
        model.addAttribute("flatNumberPrint", flat.getFlatNumber() != null ? flat.getFlatNumber().trim() : "—");
        model.addAttribute("floorPhrasePrint", ordinalFloorPhrase(flat.getFloorNumber()));
        model.addAttribute(
                "projectNamePrint",
                building.getBuildingName() != null ? building.getBuildingName().trim() : "—");
        model.addAttribute("siteAddressPrint", formatSiteAddress(building));
        model.addAttribute(
                "builderCompanyPrint",
                receipt.getBuilder().getCompanyName() != null
                        ? receipt.getBuilder().getCompanyName().trim()
                        : "—");
        model.addAttribute(
                "showChequeRealizationDisclaimer",
                receipt.getPaymentMode() != null && receipt.getPaymentMode().trim().equalsIgnoreCase("Cheque"));
    }

    private static String formatDate(LocalDate d) {
        return d != null ? d.format(PRINT_DATE) : "—";
    }

    private static String resolvePayerNames(Client client) {
        if (client.getNamePlateInfo() != null && !client.getNamePlateInfo().isBlank()) {
            return client.getNamePlateInfo().trim();
        }
        if (client.getCompanyName() != null && !client.getCompanyName().isBlank()) {
            return client.getCompanyName().trim();
        }
        return client.displayName();
    }

    private static String buildPaymentInstrument(Receipt receipt) {
        String mode = trimToEmpty(receipt.getPaymentMode());
        String ref = trimToEmpty(receipt.getChequeNo());
        if (mode.isEmpty() && ref.isEmpty()) {
            return "—";
        }
        if (ref.isEmpty()) {
            return mode;
        }
        if (mode.isEmpty()) {
            return "Ref. No. " + ref;
        }
        return mode + " No. " + ref;
    }

    private static String resolveDrawnOn(Receipt receipt) {
        String bankName = trimToNull(receipt.getBankName());
        if (bankName != null) {
            return bankName;
        }
        Bank deposit = receipt.getDepositBank();
        if (deposit == null) {
            return "—";
        }
        StringBuilder sb = new StringBuilder(deposit.getBankName().trim());
        if (deposit.getBranch() != null && !deposit.getBranch().isBlank()) {
            sb.append(" - ").append(deposit.getBranch().trim());
        }
        return sb.toString();
    }

    private static String buildPurposeNarrative(Receipt receipt) {
        StringBuilder sb = new StringBuilder();
        appendIfPositive(sb, "Consideration", receipt.getAmountConsideration());
        appendIfPositive(sb, "Extra charges", receipt.getAmountExtraCharges());
        appendIfPositive(sb, "Interest (agreement)", receipt.getAmountInterestAgreement());
        appendIfPositive(sb, "Interest (GST)", receipt.getAmountInterestGst());
        appendIfPositive(sb, "TDS", receipt.getAmountTds());
        appendIfPositive(sb, "GST", receipt.getAmountGstComponent());
        if (sb.length() == 0) {
            return "Amount received";
        }
        return sb.toString();
    }

    private static void appendIfPositive(StringBuilder sb, String label, BigDecimal amt) {
        if (amt != null && amt.compareTo(ZERO) > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(label);
        }
    }

    private static String ordinalFloorPhrase(Integer floorNumber) {
        if (floorNumber == null) {
            return "—";
        }
        int n = floorNumber;
        if (n == 0) {
            return "Ground Floor";
        }
        return ordinalEnglish(n) + " Floor";
    }

    private static String ordinalEnglish(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    private static String formatSiteAddress(Building b) {
        StringBuilder sb = new StringBuilder();
        if (b.getAddress() != null && !b.getAddress().isBlank()) {
            sb.append(b.getAddress().trim());
        }
        if (b.getCity() != null && !b.getCity().isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(b.getCity().trim());
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
