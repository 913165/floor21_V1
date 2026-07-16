package com.floor21.service;

import com.floor21.dto.SlabScheduleLineView;
import com.floor21.entity.Booking;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.entity.Receipt;
import com.floor21.entity.User;
import com.floor21.repository.UserRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.util.DocxTokenReplacer;
import com.floor21.util.IndianRupeesFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgreementWordService {

    private static final String TEMPLATE = "/agreement/default-agreement.docx";
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.ENGLISH);
    private static final BigDecimal SQFT_TO_SQM = new BigDecimal("0.092903");

    private final BookingService bookingService;
    private final BookingOwnerService bookingOwnerService;
    private final BookingPaymentSlabService bookingPaymentSlabService;
    private final ReceiptService receiptService;
    private final UserRepository userRepository;

    public AgreementWordService(
            BookingService bookingService,
            BookingOwnerService bookingOwnerService,
            BookingPaymentSlabService bookingPaymentSlabService,
            ReceiptService receiptService,
            UserRepository userRepository) {
        this.bookingService = bookingService;
        this.bookingOwnerService = bookingOwnerService;
        this.bookingPaymentSlabService = bookingPaymentSlabService;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generate(UUID bookingId) {
        Booking booking = bookingService.get(bookingId);
        List<Client> owners = bookingOwnerService.ownersInOrder(booking);
        List<SlabScheduleLineView> schedule = bookingPaymentSlabService.listLineViews(bookingId);
        List<Receipt> receipts = receiptService.listHistoryForBooking(bookingId).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDishonoured()))
                .toList();
        User loginUser = currentUser();

        try (InputStream in = AgreementWordService.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IllegalStateException("Agreement template not found: " + TEMPLATE);
            }
            try (XWPFDocument doc = new XWPFDocument(in);
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                DocxTokenReplacer.replaceAll(doc, replacements(booking, owners, receipts, loginUser));
                fillPaymentSchedule(doc, schedule);
                fillReceiptRows(doc, receipts);
                doc.write(out);
                return out.toByteArray();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate agreement document", ex);
        }
    }

    public String suggestedFilename(Booking booking) {
        String code = booking.getBookingCode() != null ? booking.getBookingCode() : booking.getId().toString();
        return "Agreement_" + code.replaceAll("[^a-zA-Z0-9_-]", "_") + ".docx";
    }

    private static Map<String, String> replacements(
            Booking booking, List<Client> owners, List<Receipt> receipts, User loginUser) {
        Map<String, String> tokens = new LinkedHashMap<>();
        Flat flat = booking.getFlat();
        Client primary = owners.isEmpty() ? booking.getClient() : owners.get(0);
        Client second = owners.size() > 1 ? owners.get(1) : null;
        String allottees =
                owners.isEmpty()
                        ? displayName(primary).toUpperCase(Locale.ROOT)
                        : owners.stream()
                                .map(AgreementWordService::displayName)
                                .map(n -> n.toUpperCase(Locale.ROOT))
                                .collect(Collectors.joining(", "));
        String address = clientAddress(primary);
        String promoter =
                booking.getBuilder() != null
                        ? dash(booking.getBuilder().getCompanyName()).toUpperCase(Locale.ROOT)
                        : "—";
        String coPromoter =
                loginUser != null && present(loginUser.getCompanyName())
                        ? loginUser.getCompanyName().trim().toUpperCase(Locale.ROOT)
                        : promoter;
        String promoterAddress =
                booking.getBuilder() != null
                        ? join(booking.getBuilder().getAddress(), booking.getBuilder().getCity())
                        : "—";
        String companyAddress = loginUser != null ? dash(loginUser.getAddress()) : promoterAddress;
        String companyPan = loginUser != null ? dash(loginUser.getPanNumber()) : "—";
        String companyEmail = loginUser != null ? dash(loginUser.getEmail()) : "—";
        String signatory = loginUser != null ? dash(loginUser.getFullName()) : "Authorised Signatory";
        BigDecimal consideration = zero(booking.getConsiderationAmt());
        BigDecimal received = receipts.stream().map(Receipt::getAmount).map(AgreementWordService::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = consideration.subtract(received).max(BigDecimal.ZERO);
        String floor = flat != null ? ordinal(flat.getFloorNumber()) + " Floor" : "—";
        String carpetSqm = squareMetres(flat != null ? flat.getCarpetAreaSqft() : null);
        String balconySqm = squareMetres(flat != null ? flat.getBalconyAreaSqft() : null);
        String parking = dash(booking.getParkingInfo());
        LocalDate agreementDate = booking.getAgreementDate();
        String agreementDateText =
                agreementDate != null ? LONG_DATE.format(agreementDate) : "__________";

        tokens.put("______ July, 2025", agreementDateText);
        tokens.put("____ July, 2025", agreementDateText);

        tokens.put("M/S. SEAVISTA INFRASTRUCTURE LLP", "M/S. " + promoter);
        tokens.put("M/s. Seavista Infrastructure LLP", "M/s. " + promoter);
        tokens.put("SEAVISTA INFRASTRUCTURE LLP", promoter);
        tokens.put("M/S. PANKAJA DEVELOPERS", "M/S. " + coPromoter);
        tokens.put("M/s. Pankaja Developers", "M/s. " + coPromoter);
        tokens.put("PANKAJA DEVELOPERS", coPromoter);
        tokens.put("AFGFS8624D", companyPan);
        tokens.put("AJHPG2076Q", companyPan);
        tokens.put("Mr. Shafique Ahmed Jameel Ahmed Ansari", "Authorised Signatory");
        tokens.put("Mr. Navin S. Bhatla", "Authorised Signatory");
        tokens.put("Mr. Pankaj Dharmendra Gupta", signatory);
        tokens.put("Mr. Pankaj Gupta", signatory);
        tokens.put("seavistainfra@gmail.com", companyEmail);
        tokens.put("pgupta1387@gmail.com", companyEmail);
        tokens.put(
                "Plot No. 17+31+32, Sector -13, Nerul, Navi Mumbai-400706, Maharashtra, India.",
                promoterAddress);
        tokens.put(
                "Office at Ground Floor, Plot no. 151, Kumars, Sector-21, Nerul, Navi Mumbai- 400706, Maharashtra, India.",
                companyAddress);

        tokens.put(
                "MR. JITIN JOSE (PAN: AGOPJ8711H) Adult, Individual, Age 45 years, occupation: Salaried, having address at B-103, Kunal 1, Plot no.204, Opp. Nerul Police Station, Sector-21,  Nerul, Navi Mumbai, Nerul Node-3, Thane, Maharashtra-400706, India, and",
                clientPartyClause(primary) + (second != null ? ", and" : ""));
        tokens.put(
                "MRS. JENNY JITIN (PAN: AFZPT4708E) Adult, Individual, Age 40 years, occupation: Salaried, having address at B-103, Kunal 1, Plot no.204, Opp. Nerul Police Station, Sector-21,  Nerul, Navi Mumbai, Nerul Node-3, Thane, Maharashtra-400706, India.",
                second != null ? clientPartyClause(second) + "." : "");
        tokens.put("MR. JITIN JOSE, MRS. JENNY JITIN", allottees);
        tokens.put("MR. JITIN JOSE", displayName(primary).toUpperCase(Locale.ROOT));
        tokens.put("MRS. JENNY JITIN", second != null ? displayName(second).toUpperCase(Locale.ROOT) : "");
        tokens.put("Mr. Jitin Jose", displayName(primary));
        tokens.put("Mrs. Jenny Jitin", second != null ? displayName(second) : "");
        tokens.put("AGOPJ8711H", primary != null ? dash(primary.getPanNumber()) : "—");
        tokens.put("AFZPT4708E", second != null ? dash(second.getPanNumber()) : "—");
        tokens.put("Age 45 years", ageText(primary));
        tokens.put("Age 40 years", ageText(second));
        tokens.put("occupation: Salaried", "occupation: " + (primary != null ? dash(primary.getOccupation()) : "—"));
        tokens.put(
                "B-103, Kunal 1, Plot no.204, Opp. Nerul Police Station, Sector-21,  Nerul, Navi Mumbai, Nerul Node-3, Thane, Maharashtra-400706, India",
                address);
        tokens.put("jitin.jose@gmail.com", primary != null ? dash(primary.getEmail1()) : "—");

        tokens.put("Flat no. 903", "Flat no. " + (flat != null ? dash(flat.getFlatNumber()) : "—"));
        tokens.put("Flat no.903", "Flat no." + (flat != null ? dash(flat.getFlatNumber()) : "—"));
        tokens.put("9th Floor", floor);
        tokens.put("62.48 sq. meters", carpetSqm + " sq. meters");
        tokens.put("3.96 sq. meters", balconySqm + " sq. meters");
        tokens.put(
                "One (1) no. of covered car parking space at Podium level P5 bearing unit no.09 admeasuring 12.50 sq. meters",
                parking);
        tokens.put("LA VESTA", projectName(flat).toUpperCase(Locale.ROOT));
        tokens.put("La Vesta", projectName(flat));
        tokens.put("MUMS33415L", loginUser != null ? dash(loginUser.getTanNumber()) : "—");

        tokens.put("Rs.1,00,00,000 /-", figures(consideration));
        tokens.put("Rs. 1,00,00,000 /-", figures(consideration));
        tokens.put("Rs. 1,00,00,000/-", figures(consideration));
        tokens.put("Rupees  One Crore  Only", IndianRupeesFormatter.formatWordsOnly(consideration));
        tokens.put("Rs. 49,50,000 /-", figures(received));
        tokens.put("49,50,000 /-", figures(received).replace("Rs. ", ""));
        tokens.put(
                "Rupees Fifty  Lakh Fifty  Thousand  Only",
                IndianRupeesFormatter.formatWordsOnly(received));
        tokens.put("Rs. 4950000 /-", figures(balance));
        tokens.put("4950000 /-", figures(balance).replace("Rs. ", ""));
        tokens.put(
                "Rupees Forty Nine Lakh Fifty  Thousand  Only",
                IndianRupeesFormatter.formatWordsOnly(balance));
        return tokens;
    }

    private static void fillPaymentSchedule(
            XWPFDocument doc, List<SlabScheduleLineView> schedule) {
        XWPFTable table = findTable(doc, "Stage of Payment");
        if (table == null) {
            return;
        }
        int totalIndex = findRow(table, "Total");
        int firstData = 1;
        int slots = Math.max(0, totalIndex - firstData);
        for (int i = 0; i < slots; i++) {
            XWPFTableRow row = table.getRow(firstData + i);
            if (i < schedule.size()) {
                var slab = schedule.get(i).slab();
                setCell(row, 0, String.valueOf(i + 1));
                setCell(row, 1, dash(slab.getMilestoneLabel()));
                setCell(
                        row,
                        2,
                        slab.getPercent() != null
                                ? slab.getPercent().stripTrailingZeros().toPlainString() + "%"
                                : "—");
            } else {
                setCell(row, 0, "");
                setCell(row, 1, "");
                setCell(row, 2, "");
            }
        }
    }

    private static void fillReceiptRows(XWPFDocument doc, List<Receipt> receipts) {
        XWPFTable table = findTable(doc, "Chq_No");
        if (table == null) {
            return;
        }
        int firstData = 1;
        for (int i = firstData; i < table.getNumberOfRows(); i++) {
            XWPFTableRow row = table.getRow(i);
            int receiptIndex = i - firstData;
            if (receiptIndex < receipts.size()) {
                Receipt receipt = receipts.get(receiptIndex);
                setCell(row, 0, String.valueOf(receiptIndex + 1));
                setCell(row, 1, IndianRupeesFormatter.formatComma(receipt.getAmount()) + "/-");
                setCell(row, 2, join(receipt.getBankName(), receipt.getPaymentMode()));
                setCell(row, 3, dash(receipt.getChequeNo()));
                setCell(
                        row,
                        4,
                        receipt.getChequeDate() != null
                                ? receipt.getChequeDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                : "");
            } else {
                for (int cell = 0; cell < row.getTableCells().size(); cell++) {
                    setCell(row, cell, "");
                }
            }
        }
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return null;
        }
        return userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(principal.getEmail()).orElse(null);
    }

    private static XWPFTable findTable(XWPFDocument doc, String text) {
        for (XWPFTable table : doc.getTables()) {
            if (table.getText() != null && table.getText().contains(text)) {
                return table;
            }
        }
        return null;
    }

    private static int findRow(XWPFTable table, String text) {
        for (int i = 0; i < table.getNumberOfRows(); i++) {
            if (table.getRow(i).getTableCells().stream()
                    .anyMatch(c -> c.getText() != null && c.getText().contains(text))) {
                return i;
            }
        }
        return table.getNumberOfRows();
    }

    private static void setCell(XWPFTableRow row, int index, String value) {
        if (row == null || index >= row.getTableCells().size()) {
            return;
        }
        XWPFTableCell cell = row.getCell(index);
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
        cell.addParagraph().createRun().setText(value != null ? value : "");
    }

    private static String displayName(Client client) {
        return client != null ? client.displayName() : "—";
    }

    private static String clientAddress(Client client) {
        if (client == null) {
            return "—";
        }
        String communication =
                join(
                        client.getCommAddress1(),
                        client.getCommAddress2(),
                        client.getCommAddress3(),
                        client.getCommCity());
        return !"—".equals(communication)
                ? communication
                : join(client.getAddress1(), client.getAddress2(), client.getAddress3(), client.getCity());
    }

    private static String ageText(Client client) {
        if (client == null || client.getDob() == null) {
            return "Adult";
        }
        return "Age " + Period.between(client.getDob(), LocalDate.now()).getYears() + " years";
    }

    private static String clientPartyClause(Client client) {
        if (client == null) {
            return "—";
        }
        return displayName(client).toUpperCase(Locale.ROOT)
                + " (PAN: "
                + dash(client.getPanNumber())
                + ") Adult, Individual, "
                + ageText(client)
                + ", occupation: "
                + dash(client.getOccupation())
                + ", having address at "
                + clientAddress(client);
    }

    private static String squareMetres(BigDecimal sqft) {
        if (sqft == null) {
            return "—";
        }
        return sqft.multiply(SQFT_TO_SQM).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String projectName(Flat flat) {
        return flat != null && flat.getBuilding() != null
                ? dash(flat.getBuilding().getBuildingName())
                : "—";
    }

    private static String figures(BigDecimal amount) {
        return IndianRupeesFormatter.formatFigures(zero(amount)).replace("/-", " /-");
    }

    private static BigDecimal zero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String dash(String value) {
        return present(value) ? value.trim() : "—";
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String join(String... values) {
        String joined =
                java.util.Arrays.stream(values)
                        .filter(AgreementWordService::present)
                        .map(String::trim)
                        .collect(Collectors.joining(", "));
        return joined.isBlank() ? "—" : joined;
    }

    private static String ordinal(Integer number) {
        if (number == null) {
            return "—";
        }
        int mod100 = number % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return number + "th";
        }
        return switch (number % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }
}
