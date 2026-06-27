package com.floor21.service;

import com.floor21.dto.ReceiptLetterView;
import com.floor21.dto.ReceiptPaymentTableRow;
import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Receipt;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.util.AreaUnits;
import com.floor21.util.IndianRupeesFormatter;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class ReceiptPrintService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter TABLE_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FULL_MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final BookingOwnerService bookingOwnerService;
    private final ReceiptSlabAllocationService receiptSlabAllocationService;
    private final BookingPaymentSlabService bookingPaymentSlabService;

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
        BigDecimal totalConsideration = resolveTotalConsideration(booking);
        LocalDate headlineDate = anchor.getReceiptDate();
        boolean combinedPrint = ordered.size() > 1;
        boolean chequeDisclaimer = ordered.stream()
                .anyMatch(r -> r.getPaymentMode() != null && r.getPaymentMode().trim().equalsIgnoreCase("Cheque"));
        return new ReceiptLetterView(
                formatOrdinalReceiptDate(headlineDate),
                IndianRupeesFormatter.formatFigures(totalAmount),
                IndianRupeesFormatter.formatWordsOnly(totalAmount),
                payerNames,
                resolveCombinedPaymentWay(ordered),
                IndianRupeesFormatter.formatFigures(totalConsideration),
                IndianRupeesFormatter.formatWordsOnly(totalConsideration),
                buildUnitDescription(flat),
                building.getBuildingName() != null ? building.getBuildingName().trim() : "—",
                buildLandAddress(building, builder),
                buildPaymentTableRows(ordered),
                resolvePlace(building, builder),
                signatoryCompanyForBuilder(builder),
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
        model.addAttribute("receiptDateOrdinal", view.receiptDateOrdinal());
        model.addAttribute("amountFiguresPrint", view.amountFiguresPrint());
        model.addAttribute("amountWordsPrint", view.amountWordsPrint());
        model.addAttribute("payerNamesPrint", view.payerNamesPrint());
        model.addAttribute("paymentWayPrint", view.paymentWayPrint());
        model.addAttribute("totalConsiderationFiguresPrint", view.totalConsiderationFiguresPrint());
        model.addAttribute("totalConsiderationWordsPrint", view.totalConsiderationWordsPrint());
        model.addAttribute("unitDescriptionPrint", view.unitDescriptionPrint());
        model.addAttribute("projectNamePrint", view.projectNamePrint());
        model.addAttribute("landAddressPrint", view.landAddressPrint());
        model.addAttribute("paymentTableRows", view.paymentTableRows());
        model.addAttribute("placePrint", view.placePrint());
        model.addAttribute("builderCompanyPrint", view.builderCompanyPrint());
        model.addAttribute("showChequeRealizationDisclaimer", view.showChequeRealizationDisclaimer());
        model.addAttribute("combinedPrint", view.combinedPrint());
    }

    private List<ReceiptPaymentTableRow> buildPaymentTableRows(List<Receipt> receipts) {
        List<ReceiptPaymentTableRow> rows = new ArrayList<>();
        int serial = 1;
        for (Receipt receipt : receipts) {
            rows.add(
                    new ReceiptPaymentTableRow(
                            serial++,
                            formatTableDate(receipt.getReceiptDate()),
                            buildTableInstrumentDetail(receipt),
                            formatTableAmount(receipt.getAmount())));
        }
        return rows;
    }

    private String resolveCombinedPaymentWay(List<Receipt> receipts) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (Receipt receipt : receipts) {
            String label = resolvePaymentWay(receipt);
            if (label != null && !label.isBlank() && !"—".equals(label)) {
                labels.add(label);
            }
        }
        if (labels.isEmpty()) {
            return "Amount received";
        }
        return String.join(", ", labels);
    }

    private static String formatTableDate(LocalDate d) {
        return d != null ? d.format(TABLE_DATE) : "";
    }

    private static String formatTableAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            return "—";
        }
        return IndianRupeesFormatter.formatComma(amount) + "/-";
    }

    private static String formatOrdinalReceiptDate(LocalDate d) {
        if (d == null) {
            return "—";
        }
        return ordinalEnglish(d.getDayOfMonth()) + " " + d.format(FULL_MONTH_YEAR);
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

    private String resolvePaymentWay(Receipt receipt) {
        UUID bookingId = receipt.getBooking().getId();
        UUID builderId = receipt.getBuilder().getId();
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabService.listSlabsForPaymentLedgerReadOnly(bookingId, builderId);
        if (!slabs.isEmpty()) {
            Map<UUID, List<ReceiptSlabAllocationSlice>> alloc =
                    receiptSlabAllocationService.allocateBySlab(bookingId, builderId);
            List<String> labels = new ArrayList<>();
            for (BookingPaymentSlab slab : slabs) {
                for (ReceiptSlabAllocationSlice slice : alloc.getOrDefault(slab.getId(), List.of())) {
                    if (receipt.getId().equals(slice.receiptId())
                            && slice.amount() != null
                            && slice.amount().compareTo(ZERO) > 0) {
                        labels.add(slab.getMilestoneLabel().trim());
                        break;
                    }
                }
            }
            if (!labels.isEmpty()) {
                return String.join(", ", labels);
            }
        }
        return buildPurposeNarrative(receipt);
    }

    private static String buildTableInstrumentDetail(Receipt receipt) {
        String mode = trimToEmpty(receipt.getPaymentMode());
        String ref = trimToEmpty(receipt.getChequeNo());
        if (!mode.isEmpty() && !ref.isEmpty()) {
            return mode + " " + ref;
        }
        String label = SlabReceiptWaterfall.buildChequeLabel(receipt);
        if (label != null && !label.isBlank()) {
            return label;
        }
        return "—";
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

    private static BigDecimal resolveTotalConsideration(Booking booking) {
        if (booking.getFinalAmount() != null && booking.getFinalAmount().compareTo(ZERO) > 0) {
            return booking.getFinalAmount();
        }
        if (booking.getConsiderationAmt() != null && booking.getConsiderationAmt().compareTo(ZERO) > 0) {
            return booking.getConsiderationAmt();
        }
        return ZERO;
    }

    private static String buildUnitDescription(Flat flat) {
        String flatNo = flat.getFlatNumber() != null ? flat.getFlatNumber().trim() : "—";
        String floor = ordinalFloorPhrase(flat.getFloorNumber());
        String carpet = AreaUnits.formatSqMetersFromSqft(flat.getCarpetAreaSqft());
        String deck = AreaUnits.formatSqMetersFromSqft(flat.getBalconyAreaSqft());
        StringBuilder sb = new StringBuilder();
        sb.append("Flat no. ").append(flatNo).append(", ").append(floor);
        if (carpet != null) {
            sb.append(", admeasuring about ")
                    .append(carpet)
                    .append(" sq. meters RERA Carpet Area");
        }
        if (deck != null) {
            if (carpet != null) {
                sb.append(" plus deck area admeasuring about ").append(deck).append(" sq. meters");
            } else {
                sb.append(", deck area admeasuring about ").append(deck).append(" sq. meters");
            }
        }
        return sb.toString();
    }

    private static String buildLandAddress(Building building, Builder builder) {
        String address =
                coalesceText(
                        building != null ? building.getAddress() : null,
                        builder != null ? builder.getAddress() : null);
        String city =
                coalesceText(
                        building != null ? building.getCity() : null,
                        builder != null ? builder.getCity() : null);
        String fullLocation = joinAddressParts(address, city);
        if (fullLocation.isEmpty()) {
            return "to be constructed on all that piece and parcel of land as per project records";
        }

        String lower = fullLocation.toLowerCase(Locale.ENGLISH);
        if (lower.startsWith("to be constructed on")) {
            return fullLocation;
        }
        if (lower.startsWith("all that piece and parcel of land")) {
            return "to be constructed on " + fullLocation;
        }
        if (lower.contains("bearing ") || lower.contains("plot no") || lower.contains("plot no.")) {
            if (lower.contains("bearing ")) {
                return "to be constructed on all that piece and parcel of land " + fullLocation;
            }
            return "to be constructed on all that piece and parcel of land bearing " + fullLocation;
        }
        if (lower.contains("situated at")) {
            return "to be constructed on all that piece and parcel of land " + fullLocation;
        }
        return "to be constructed on all that piece and parcel of land situated at " + fullLocation;
    }

    private static String resolvePlace(Building building, Builder builder) {
        String city =
                coalesceText(
                        building != null ? building.getCity() : null,
                        builder != null ? builder.getCity() : null);
        if (city != null) {
            return city;
        }
        return "—";
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

    /** GSTIN and TAN from the builder-admin user (User Management tax fields). */
    public BuilderTaxProfile taxProfileForBuilder(Builder builder) {
        if (builder == null) {
            return new BuilderTaxProfile("—", "—");
        }
        for (UserProjectAssignment assignment :
                userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId())) {
            if (!StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(assignment.getRole())) {
                continue;
            }
            User user = assignment.getUser();
            String gstin = trimToNull(user != null ? user.getGstNumber() : null);
            String tan = trimToNull(user != null ? user.getTanNumber() : null);
            if (gstin != null || tan != null) {
                return new BuilderTaxProfile(
                        gstin != null ? gstin : "—", tan != null ? tan : "—");
            }
        }
        return new BuilderTaxProfile("—", "—");
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

    private static String userCompanyName(User user) {
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
