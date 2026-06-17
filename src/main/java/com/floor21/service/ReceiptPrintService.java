package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Receipt;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.util.IndianRupeesFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class ReceiptPrintService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter PRINT_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final BookingOwnerService bookingOwnerService;

    public ReceiptLetterView buildLetterView(Receipt receipt) {
        return buildLetterView(receipt, false);
    }

    public ReceiptLetterView buildLetterView(Receipt receipt, boolean allOwners) {
        Flat flat = receipt.getBooking().getFlat();
        Building building = flat.getBuilding();
        Client payer =
                receipt.getPaidByClient() != null ? receipt.getPaidByClient() : receipt.getBooking().getClient();
        String payerNames =
                allOwners ? resolveAllOwnerNames(receipt.getBooking()) : resolvePayerNames(payer);
        String receiptNumber =
                receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()
                        ? receipt.getReceiptNumber().trim()
                        : "—";
        return new ReceiptLetterView(
                receiptNumber,
                formatDate(receipt.getReceiptDate()),
                formatDate(receipt.getChequeDate() != null ? receipt.getChequeDate() : receipt.getReceiptDate()),
                IndianRupeesFormatter.formatFigures(receipt.getAmount()),
                IndianRupeesFormatter.formatWordsOnly(receipt.getAmount()),
                payerNames,
                buildPaymentInstrument(receipt),
                resolveDrawnOn(receipt),
                buildPurposeNarrative(receipt),
                flat.getFlatNumber() != null ? flat.getFlatNumber().trim() : "—",
                ordinalFloorPhrase(flat.getFloorNumber()),
                building.getBuildingName() != null ? building.getBuildingName().trim() : "—",
                formatSiteAddress(building),
                signatoryCompanyForBuilder(resolveBuilder(receipt, building)),
                receipt.getPaymentMode() != null && receipt.getPaymentMode().trim().equalsIgnoreCase("Cheque"));
    }

    public void addPrintAttributes(Model model, Receipt receipt) {
        ReceiptLetterView view = buildLetterView(receipt);
        model.addAttribute("receiptDateFormatted", view.receiptDateFormatted());
        model.addAttribute("instrumentDateFormatted", view.instrumentDateFormatted());
        model.addAttribute("amountFiguresPrint", view.amountFiguresPrint());
        model.addAttribute("amountWordsPrint", view.amountWordsPrint());
        model.addAttribute("payerNamesPrint", view.payerNamesPrint());
        model.addAttribute("paymentInstrumentPrint", view.paymentInstrumentPrint());
        model.addAttribute("drawnOnBankPrint", view.drawnOnBankPrint());
        model.addAttribute("purposeNarrativePrint", view.purposeNarrativePrint());
        model.addAttribute("flatNumberPrint", view.flatNumberPrint());
        model.addAttribute("floorPhrasePrint", view.floorPhrasePrint());
        model.addAttribute("projectNamePrint", view.projectNamePrint());
        model.addAttribute("siteAddressPrint", view.siteAddressPrint());
        model.addAttribute("builderCompanyPrint", view.builderCompanyPrint());
        model.addAttribute("showChequeRealizationDisclaimer", view.showChequeRealizationDisclaimer());
    }

    private static String formatDate(LocalDate d) {
        return d != null ? d.format(PRINT_DATE) : "—";
    }

    private static String resolvePayerNames(Client client) {
        if (client == null) {
            return "—";
        }
        if (client.getNamePlateInfo() != null && !client.getNamePlateInfo().isBlank()) {
            return client.getNamePlateInfo().trim();
        }
        if (client.getCompanyName() != null && !client.getCompanyName().isBlank()) {
            return client.getCompanyName().trim();
        }
        return client.displayName();
    }

    private String resolveAllOwnerNames(Booking booking) {
        Set<String> names = new LinkedHashSet<>();
        for (Client owner : bookingOwnerService.ownersInOrder(booking)) {
            String name = resolvePayerNames(owner);
            if (name != null && !name.isBlank() && !"—".equals(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return "—";
        }
        return String.join(" & ", names);
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

    /**
     * Authorised signatory uses the builder admin's company name (User Management), not the project
     * or building marketing name shown in the receipt narrative.
     */
    public String signatoryCompanyForBuilder(Builder builder) {
        if (builder == null) {
            return "—";
        }
        for (UserProjectAssignment assignment :
                userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId())) {
            if (!StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(assignment.getRole())) {
                continue;
            }
            String company = userCompanyName(assignment.getUser());
            if (company != null) {
                return company;
            }
        }
        if (builder.getCompanyName() != null && !builder.getCompanyName().isBlank()) {
            return builder.getCompanyName().trim();
        }
        return "—";
    }

    private static Builder resolveBuilder(Receipt receipt, Building building) {
        if (building != null && building.getBuilder() != null) {
            return building.getBuilder();
        }
        return receipt.getBuilder();
    }

    private static String userCompanyName(User user) {
        if (user == null || user.getCompanyName() == null || user.getCompanyName().isBlank()) {
            return null;
        }
        return user.getCompanyName().trim();
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
