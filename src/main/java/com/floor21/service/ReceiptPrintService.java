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
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.util.IndianRupeesFormatter;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class ReceiptPrintService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final BookingOwnerService bookingOwnerService;

    public ReceiptLetterView buildLetterView(Receipt receipt) {
        return buildLetterView(receipt, false);
    }

    public ReceiptLetterView buildLetterView(Receipt receipt, boolean allOwners) {
        return buildLetterView(List.of(receipt), allOwners);
    }

    public ReceiptLetterView buildCombinedLetterView(List<Receipt> receipts, boolean allOwners) {
        if (receipts == null || receipts.isEmpty()) {
            throw new IllegalArgumentException("At least one receipt is required for print");
        }
        return buildLetterView(receipts, allOwners);
    }

    private ReceiptLetterView buildLetterView(List<Receipt> receipts, boolean allOwners) {
        List<Receipt> ordered = receipts.stream()
                .sorted(Comparator.comparing(Receipt::getReceiptDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Receipt::getReceiptSerial, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Receipt::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Receipt anchor = ordered.get(ordered.size() - 1);
        Booking booking = anchor.getBooking();
        Flat flat = booking.getFlat();
        Building building = flat.getBuilding();
        Builder builder = resolveBuilder(anchor, building);
        Client payer =
                anchor.getPaidByClient() != null ? anchor.getPaidByClient() : booking.getClient();
        String payerNames =
                allOwners ? resolveAllOwnerNames(booking) : resolvePayerNames(payer);
        BigDecimal totalAmount = ordered.stream()
                .map(Receipt::getAmount)
                .filter(a -> a != null)
                .reduce(ZERO, BigDecimal::add);
        boolean combinedPrint = ordered.size() > 1;
        boolean chequeDisclaimer = ordered.stream()
                .anyMatch(r -> r.getPaymentMode() != null && r.getPaymentMode().trim().equalsIgnoreCase("Cheque"));
        BuilderContact contact = resolveBuilderContact(builder);
        return new ReceiptLetterView(
                formatReceiptNumbers(ordered),
                formatShortDate(anchor.getReceiptDate()),
                payerNames,
                IndianRupeesFormatter.formatFigures(totalAmount),
                IndianRupeesFormatter.formatWordsOnly(totalAmount),
                buildCombinedInstrumentNarrative(ordered),
                resolveCombinedPaymentPurpose(ordered),
                formatFlatNumber(flat),
                ordinalFloorPhrase(flat.getFloorNumber()),
                building.getBuildingName() != null ? building.getBuildingName().trim() : "—",
                buildSiteAddress(building, builder),
                signatoryCompanyForBuilder(builder),
                contact.address(),
                contact.phone(),
                contact.email(),
                chequeDisclaimer,
                combinedPrint);
    }

    public void addPrintAttributes(Model model, Receipt receipt) {
        addPrintAttributes(model, receipt, false);
    }

    public void addPrintAttributes(Model model, Receipt receipt, boolean allOwners) {
        addPrintAttributes(model, buildLetterView(receipt, allOwners));
    }

    public void addPrintAttributes(Model model, ReceiptLetterView view) {
        model.addAttribute("receiptNumberPrint", view.receiptNumberPrint());
        model.addAttribute("receiptDateShort", view.receiptDateShort());
        model.addAttribute("payerNamesPrint", view.payerNamesPrint());
        model.addAttribute("amountFiguresPrint", view.amountFiguresPrint());
        model.addAttribute("amountWordsPrint", view.amountWordsPrint());
        model.addAttribute("instrumentNarrativePrint", view.instrumentNarrativePrint());
        model.addAttribute("paymentPurposePrint", view.paymentPurposePrint());
        model.addAttribute("flatNumberPrint", view.flatNumberPrint());
        model.addAttribute("floorPhrasePrint", view.floorPhrasePrint());
        model.addAttribute("projectNamePrint", view.projectNamePrint());
        model.addAttribute("siteAddressPrint", view.siteAddressPrint());
        model.addAttribute("builderCompanyPrint", view.builderCompanyPrint());
        model.addAttribute("footerAddressPrint", view.footerAddressPrint());
        model.addAttribute("footerPhonePrint", view.footerPhonePrint());
        model.addAttribute("footerEmailPrint", view.footerEmailPrint());
        model.addAttribute("showChequeRealizationDisclaimer", view.showChequeRealizationDisclaimer());
        model.addAttribute("combinedPrint", view.combinedPrint());
    }

    private static String formatReceiptNumbers(List<Receipt> receipts) {
        return receipts.stream()
                .map(ReceiptPrintService::displayReceiptNumber)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
    }

    private static String displayReceiptNumber(Receipt receipt) {
        if (receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()) {
            return receipt.getReceiptNumber().trim();
        }
        if (receipt.getReceiptSerial() != null) {
            return String.valueOf(receipt.getReceiptSerial());
        }
        return "—";
    }

    private static String formatShortDate(LocalDate d) {
        return d != null ? d.format(SHORT_DATE) : "—";
    }

    private static String formatFlatNumber(Flat flat) {
        if (flat == null || flat.getFlatNumber() == null || flat.getFlatNumber().isBlank()) {
            return "—";
        }
        return "Flat No." + flat.getFlatNumber().trim();
    }

    private String buildCombinedInstrumentNarrative(List<Receipt> receipts) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (Receipt receipt : receipts) {
            String narrative = buildInstrumentNarrative(receipt);
            if (narrative != null && !narrative.isBlank()) {
                parts.add(narrative);
            }
        }
        if (parts.isEmpty()) {
            return "—";
        }
        return String.join("; ", parts);
    }

    private String resolveCombinedPaymentPurpose(List<Receipt> receipts) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (Receipt receipt : receipts) {
            String label = resolvePaymentPurpose(receipt);
            if (label != null && !label.isBlank() && !"—".equals(label)) {
                labels.add(label);
            }
        }
        if (labels.isEmpty()) {
            return "Consideration";
        }
        return String.join(", ", labels);
    }

    private static String buildInstrumentNarrative(Receipt receipt) {
        String mode = trimToEmpty(receipt.getPaymentMode());
        String ref = trimToEmpty(receipt.getChequeNo());
        LocalDate instrumentDate =
                receipt.getChequeDate() != null ? receipt.getChequeDate() : receipt.getReceiptDate();
        String dateStr = formatShortDate(instrumentDate);
        String bankDrawn = buildBankDrawnOn(receipt);
        String bankSuffix =
                bankDrawn.isEmpty() ? "" : ", drawn on " + bankDrawn;

        if (mode.equalsIgnoreCase("Cheque") && !ref.isEmpty()) {
            return "Cheque No. " + ref + " dated " + dateStr + bankSuffix;
        }
        if (!ref.isEmpty()) {
            String label =
                    mode.isEmpty()
                            ? "Payment"
                            : mode.equalsIgnoreCase("NEFT")
                                    ? "NEFT"
                                    : mode.equalsIgnoreCase("RTGS")
                                            ? "RTGS"
                                            : mode.equalsIgnoreCase("UPI")
                                                    ? "UPI"
                                                    : mode;
            return label + " No. " + ref + " dated " + dateStr + bankSuffix;
        }
        String label = SlabReceiptWaterfall.buildChequeLabel(receipt);
        if (label != null && !label.isBlank()) {
            return label + " dated " + dateStr + bankSuffix;
        }
        if (!mode.isEmpty()) {
            return mode + " dated " + dateStr + bankSuffix;
        }
        return "Payment dated " + dateStr + bankSuffix;
    }

    private static String buildBankDrawnOn(Receipt receipt) {
        String bank = trimToEmpty(receipt.getBankName());
        if (!bank.isEmpty()) {
            return bank;
        }
        if (receipt.getDepositBank() == null) {
            return "";
        }
        String depositName =
                receipt.getDepositBank().getBankName() != null
                        ? receipt.getDepositBank().getBankName().trim()
                        : "";
        String branch =
                receipt.getDepositBank().getBranch() != null
                        ? receipt.getDepositBank().getBranch().trim()
                        : "";
        if (!depositName.isEmpty() && !branch.isEmpty()) {
            return depositName + " - " + branch;
        }
        return depositName;
    }

    private static String resolvePaymentPurpose(Receipt receipt) {
        if (receipt.getAmountConsideration() != null
                && receipt.getAmountConsideration().compareTo(ZERO) > 0) {
            return "Consideration";
        }
        StringBuilder sb = new StringBuilder();
        appendIfPositive(sb, "Extra charges", receipt.getAmountExtraCharges());
        appendIfPositive(sb, "Interest (agreement)", receipt.getAmountInterestAgreement());
        appendIfPositive(sb, "Interest (GST)", receipt.getAmountInterestGst());
        appendIfPositive(sb, "TDS", receipt.getAmountTds());
        appendIfPositive(sb, "GST", receipt.getAmountGstComponent());
        if (sb.length() == 0) {
            return "Consideration";
        }
        return sb.toString();
    }

    private static String buildSiteAddress(Building building, Builder builder) {
        String address =
                coalesceText(
                        building != null ? building.getAddress() : null,
                        builder != null ? builder.getAddress() : null);
        String city =
                coalesceText(
                        building != null ? building.getCity() : null,
                        builder != null ? builder.getCity() : null);
        String joined = joinAddressParts(address, city);
        return joined.isEmpty() ? "—" : joined;
    }

    private BuilderContact resolveBuilderContact(Builder builder) {
        if (builder == null) {
            return new BuilderContact("—", "—", "—");
        }
        User admin = findBuilderAdminUser(builder);
        String address =
                coalesceText(
                        admin != null ? admin.getAddress() : null, builder.getAddress());
        String phone =
                coalesceText(
                        admin != null ? admin.getMobileNumber() : null, builder.getPhone());
        String email =
                coalesceText(admin != null ? admin.getEmail() : null, builder.getEmail());
        return new BuilderContact(
                address != null ? address : "—",
                phone != null ? phone : "—",
                email != null ? email : "—");
    }

    private User findBuilderAdminUser(Builder builder) {
        for (UserProjectAssignment assignment :
                userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId())) {
            if (!StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(assignment.getRole())) {
                continue;
            }
            return assignment.getUser();
        }
        return null;
    }

    private record BuilderContact(String address, String phone, String email) {}

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
        return String.join(", ", names);
    }

    private static String coalesceText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static String joinAddressParts(String address, String city) {
        if (address == null && city == null) {
            return "";
        }
        if (address == null) {
            return city;
        }
        if (city == null) {
            return address;
        }
        String addressLower = address.toLowerCase(Locale.ENGLISH);
        String cityLower = city.toLowerCase(Locale.ENGLISH);
        if (addressLower.contains(cityLower)) {
            return address;
        }
        if (addressLower.contains("situated at")) {
            return address.endsWith(",") ? address + " " + city : address + ", " + city;
        }
        return address + ", " + city;
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

    public String signatoryCompanyForBuilder(Builder builder) {
        if (builder == null) {
            return "—";
        }
        List<UserProjectAssignment> assignments =
                userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId());
        String currentEmail = currentPrincipalEmail();
        if (currentEmail != null) {
            for (UserProjectAssignment assignment : assignments) {
                User user = assignment.getUser();
                if (user != null && currentEmail.equalsIgnoreCase(user.getEmail())) {
                    String company = userLegalCompanyName(user);
                    if (company != null) {
                        return company;
                    }
                }
            }
        }
        for (UserProjectAssignment assignment : assignments) {
            if (!StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(assignment.getRole())) {
                continue;
            }
            String company = userLegalCompanyName(assignment.getUser());
            if (company != null) {
                return company;
            }
        }
        for (UserProjectAssignment assignment : assignments) {
            String company = userLegalCompanyName(assignment.getUser());
            if (company != null) {
                return company;
            }
        }
        if (assignments.isEmpty()
                && builder.getCompanyName() != null
                && !builder.getCompanyName().isBlank()) {
            return builder.getCompanyName().trim();
        }
        return "—";
    }

    private static String currentPrincipalEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return null;
        }
        return principal.getEmail();
    }

    /** GSTIN and TAN from project users (builder admin first, then any assigned user). */
    public BuilderTaxProfile taxProfileForBuilder(Builder builder) {
        if (builder == null) {
            return new BuilderTaxProfile("—", "—");
        }
        String gstin = null;
        String tan = null;
        List<UserProjectAssignment> assignments =
                userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId());
        for (UserProjectAssignment assignment : assignments) {
            if (!StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(assignment.getRole())) {
                continue;
            }
            User user = assignment.getUser();
            if (user == null) {
                continue;
            }
            if (gstin == null) {
                gstin = trimToNull(user.getGstNumber());
            }
            if (tan == null) {
                tan = trimToNull(user.getTanNumber());
            }
            if (gstin != null && tan != null) {
                break;
            }
        }
        if (gstin == null || tan == null) {
            for (UserProjectAssignment assignment : assignments) {
                User user = assignment.getUser();
                if (user == null) {
                    continue;
                }
                if (gstin == null) {
                    gstin = trimToNull(user.getGstNumber());
                }
                if (tan == null) {
                    tan = trimToNull(user.getTanNumber());
                }
                if (gstin != null && tan != null) {
                    break;
                }
            }
        }
        return new BuilderTaxProfile(
                gstin != null ? gstin : "—", tan != null ? tan : "—");
    }

    public String floorPhraseForFlat(Flat flat) {
        return ordinalFloorPhrase(flat != null ? flat.getFloorNumber() : null);
    }

    public record BuilderTaxProfile(String gstin, String tan) {}

    private static Builder resolveBuilder(Receipt receipt, Building building) {
        if (building != null && building.getBuilder() != null) {
            return building.getBuilder();
        }
        return receipt.getBuilder();
    }

    private static String userLegalCompanyName(User user) {
        if (user == null || user.getCompanyName() == null || user.getCompanyName().isBlank()) {
            return null;
        }
        return user.getCompanyName().trim();
    }

    private static void appendIfPositive(StringBuilder sb, String label, BigDecimal amt) {
        if (amt != null && amt.compareTo(ZERO) > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(label);
        }
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
