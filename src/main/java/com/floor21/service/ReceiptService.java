package com.floor21.service;

import com.floor21.dto.BookingReceiptSummary;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.Client;
import com.floor21.entity.Receipt;
import com.floor21.entity.User;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BankRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.repository.UserRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReceiptRepository receiptRepository;
    private final BookingRepository bookingRepository;
    private final BuilderRepository builderRepository;
    private final BankRepository bankRepository;
    private final ClientRepository clientRepository;
    private final BookingOwnerService bookingOwnerService;
    private final UserRepository userRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @Transactional(readOnly = true)
    public BigDecimal totalForBooking(UUID bookingId) {
        return totalForBooking(bookingId, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForBooking(UUID bookingId, UUID platformBuilderId) {
        UUID builderId = effectiveBuilderId(platformBuilderId);
        requireAccessibleBooking(bookingId, platformBuilderId);
        return receiptRepository.sumAmountForBooking(bookingId, builderId);
    }

    @Transactional(readOnly = true)
    public List<Receipt> listHistoryForBooking(UUID bookingId) {
        return listHistoryForBooking(bookingId, null);
    }

    @Transactional(readOnly = true)
    public List<Receipt> listHistoryForBooking(UUID bookingId, UUID platformBuilderId) {
        UUID builderId = effectiveBuilderId(platformBuilderId);
        requireAccessibleBooking(bookingId, platformBuilderId);
        return receiptRepository.findByBooking_IdAndBuilder_IdOrderByReceiptSerialAscCreatedAtAsc(
                bookingId, builderId);
    }

    @Transactional(readOnly = true)
    public List<Receipt> listForTenant() {
        List<Receipt> all = receiptRepository.findForTenantList(TenantContext.requireBuilderId());
        if (TenantContext.hasUnrestrictedBuildingAccess()) {
            return all;
        }
        return all.stream()
                .filter(
                        r ->
                                r.getBooking() != null
                                        && r.getBooking().getFlat() != null
                                        && r.getBooking().getFlat().getBuilding() != null
                                        && TenantContext.canAccessBuilding(
                                                r.getBooking().getFlat().getBuilding().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingReceiptSummary summarizeBooking(UUID bookingId) {
        return summarizeBooking(bookingId, null);
    }

    @Transactional(readOnly = true)
    public BookingReceiptSummary summarizeBooking(UUID bookingId, UUID platformBuilderId) {
        UUID builderId = effectiveBuilderId(platformBuilderId);
        Booking booking = requireAccessibleBooking(bookingId, platformBuilderId);
        BigDecimal agreementTotal = agreementBase(booking);
        BigDecimal agreementReceived = receiptRepository.sumAgreementCredits(bookingId, builderId);
        BigDecimal agreementBalance = agreementTotal.subtract(agreementReceived);

        BigDecimal gstTotal = booking.getGst() != null ? booking.getGst() : ZERO;
        BigDecimal gstReceived = receiptRepository.sumGstCredits(bookingId, builderId);
        BigDecimal gstBalance = gstTotal.subtract(gstReceived);
        boolean showGstRow =
                gstTotal.compareTo(ZERO) > 0 || gstReceived.compareTo(ZERO) > 0;

        return BookingReceiptSummary.builder()
                .agreementTotal(agreementTotal)
                .agreementReceived(agreementReceived)
                .agreementBalance(agreementBalance)
                .showGstRow(showGstRow)
                .gstTotal(gstTotal)
                .gstReceived(gstReceived)
                .gstBalance(gstBalance)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<String> latestReceiptNumberHint(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        requireAccessibleBooking(bookingId, null);
        return receiptRepository
                .findFirstByBooking_IdAndBuilder_IdOrderByReceiptSerialDesc(bookingId, builderId)
                .map(Receipt::getReceiptNumber)
                .filter(s -> s != null && !s.isBlank());
    }

    @Transactional(readOnly = true)
    public String previewNextReceiptNumber(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        requireAccessibleBooking(bookingId, null);
        int next = receiptRepository.findMaxReceiptSerialByBookingId(bookingId, builderId) + 1;
        return formatIncrementalReceiptNumber(next);
    }

    @Transactional(readOnly = true)
    public Receipt getForBooking(UUID receiptId, UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        requireAccessibleBooking(bookingId, null);
        return receiptRepository
                .findByIdAndBooking_IdAndBuilder_Id(receiptId, bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
    }

    @Transactional(readOnly = true)
    public Receipt getForPrint(UUID receiptId, UUID bookingId) {
        return getForPrint(receiptId, bookingId, null);
    }

    @Transactional(readOnly = true)
    public Receipt getForPrint(UUID receiptId, UUID bookingId, UUID platformBuilderId) {
        UUID builderId = effectiveBuilderId(platformBuilderId);
        requireAccessibleBooking(bookingId, platformBuilderId);
        return receiptRepository
                .findByIdForPrintView(receiptId, bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
    }

    @Transactional
    public Receipt save(UUID bookingId, Receipt form) {
        return saveInternal(bookingId, form, null, null);
    }

    @Transactional
    public Receipt saveImported(
            UUID bookingId, Receipt form, String receiptNumberOverride, String enteredByOverride) {
        return saveInternal(bookingId, form, receiptNumberOverride, enteredByOverride);
    }

    private Receipt saveInternal(
            UUID bookingId, Receipt form, String receiptNumberOverride, String enteredByOverride) {
        UUID builderId = TenantContext.requireBuilderId();
        boolean updating = form.getId() != null;
        Booking booking =
                updating
                        ? bookingRepository
                                .findByIdAndBuilder_Id(bookingId, builderId)
                                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"))
                        : bookingRepository
                                .findByIdAndBuilder_IdForUpdate(bookingId, builderId)
                                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        assertBookingBuildingAccess(booking);
        var builder = builderRepository.findById(builderId).orElseThrow();

        Receipt entity;
        Instant now = Instant.now();
        if (updating) {
            entity =
                    receiptRepository
                            .findByIdAndBooking_IdAndBuilder_Id(form.getId(), bookingId, builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
        } else {
            entity = new Receipt();
            entity.setBuilder(builder);
            entity.setBooking(booking);
            entity.setCreatedAt(now);
            int nextSerial =
                    receiptRepository.findMaxReceiptSerialByBookingId(bookingId, builderId) + 1;
            entity.setReceiptSerial(nextSerial);
            if (receiptNumberOverride != null && !receiptNumberOverride.isBlank()) {
                entity.setReceiptNumber(receiptNumberOverride.trim());
            } else {
                entity.setReceiptNumber(formatIncrementalReceiptNumber(nextSerial));
            }
            if (enteredByOverride != null && !enteredByOverride.isBlank()) {
                entity.setEnteredByDisplay(enteredByOverride.trim());
            } else {
                entity.setEnteredByDisplay(resolveEnteredByDisplay(builderId));
            }
        }

        entity.setReceiptDate(form.getReceiptDate() != null ? form.getReceiptDate() : LocalDate.now());
        entity.setChequeDate(form.getChequeDate());
        entity.setAmountConsideration(zeroIfNull(form.getAmountConsideration()));
        entity.setAmountExtraCharges(zeroIfNull(form.getAmountExtraCharges()));
        entity.setAmountInterestAgreement(zeroIfNull(form.getAmountInterestAgreement()));
        entity.setAmountInterestGst(zeroIfNull(form.getAmountInterestGst()));
        entity.setAmountTds(zeroIfNull(form.getAmountTds()));
        entity.setAmountGstComponent(zeroIfNull(form.getAmountGstComponent()));
        entity.setAmount(computeTotal(entity));
        entity.setPaymentMode(trimToNull(form.getPaymentMode()));
        entity.setChequeNo(trimToNull(form.getChequeNo()));
        entity.setBankName(trimToNull(form.getBankName()));
        applyDepositTarget(entity, form, builderId);
        applyPaidByClient(entity, form, booking, builderId);
        entity.setDishonoured(Boolean.TRUE.equals(form.getDishonoured()));
        entity.setRemarks(trimToNull(form.getRemarks()));

        if (entity.getAmount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Receipt total must be greater than zero. Enter at least one amount in the received breakdown.");
        }

        return receiptRepository.save(entity);
    }

    private static BigDecimal computeTotal(Receipt r) {
        return zeroIfNull(r.getAmountConsideration())
                .add(zeroIfNull(r.getAmountExtraCharges()))
                .add(zeroIfNull(r.getAmountInterestAgreement()))
                .add(zeroIfNull(r.getAmountInterestGst()))
                .add(zeroIfNull(r.getAmountTds()))
                .add(zeroIfNull(r.getAmountGstComponent()));
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    /** Plain incremental receipt no. for this booking (buyer): 1, 2, 3, … */
    private static String formatIncrementalReceiptNumber(int serial) {
        return String.valueOf(serial);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String resolveEnteredByDisplay(UUID builderId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        String email = auth.getName();
        Optional<User> staff =
                userRepository
                        .findFirstByEmailIgnoreCaseAndActiveTrue(email)
                        .filter(u -> userProjectAssignmentService.hasMembership(u.getId(), builderId));
        return staff.map(User::getFullName).orElse(email);
    }

    private void applyDepositTarget(Receipt entity, Receipt form, UUID builderId) {
        UUID depositBankId =
                form.getDepositBank() != null && form.getDepositBank().getId() != null
                        ? form.getDepositBank().getId()
                        : null;
        if (depositBankId != null) {
            Bank bank =
                    bankRepository
                            .findByIdAndBuilder_Id(depositBankId, builderId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Choose a bank account from the list, or clear the selection."));
            if (!Boolean.TRUE.equals(bank.getActive())) {
                throw new IllegalArgumentException(
                        "That bank account is inactive. Pick another or reactivate it under Bank accounts.");
            }
            entity.setDepositBank(bank);
            entity.setDepositAccount(buildDepositAccountLabel(bank));
        } else {
            entity.setDepositBank(null);
            entity.setDepositAccount(trimToNull(form.getDepositAccount()));
        }
    }

    private void applyPaidByClient(Receipt entity, Receipt form, Booking booking, UUID builderId) {
        UUID paidById =
                form.getPaidByClient() != null && form.getPaidByClient().getId() != null
                        ? form.getPaidByClient().getId()
                        : null;
        if (paidById == null) {
            entity.setPaidByClient(null);
            return;
        }
        if (!bookingOwnerService.isOwner(booking.getId(), paidById)) {
            throw new IllegalArgumentException("Paid by must be the primary client or a co-owner on this booking.");
        }
        Client payer =
                clientRepository
                        .findByIdAndBuilder_Id(paidById, builderId)
                        .orElseThrow(() -> new IllegalArgumentException("Paid-by client not found."));
        entity.setPaidByClient(payer);
    }

    private static String buildDepositAccountLabel(Bank bank) {
        StringBuilder sb = new StringBuilder(bank.getBankName().trim());
        if (bank.getBranch() != null && !bank.getBranch().isBlank()) {
            sb.append(" — ").append(bank.getBranch().trim());
        }
        if (bank.getAccountNumber() != null && !bank.getAccountNumber().isBlank()) {
            sb.append(" — ").append(bank.getAccountNumber().trim());
        }
        String s = sb.toString();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static BigDecimal agreementBase(Booking booking) {
        if (booking.getConsiderationAmt() != null
                && booking.getConsiderationAmt().compareTo(ZERO) > 0) {
            return booking.getConsiderationAmt();
        }
        if (booking.getFlat() != null && booking.getFlat().getBasePrice() != null) {
            return booking.getFlat().getBasePrice();
        }
        return ZERO;
    }

    @Transactional(readOnly = true)
    public Booking requireBookingForImport(UUID bookingId) {
        return requireAccessibleBooking(bookingId, null);
    }

    private Booking requireAccessibleBooking(UUID bookingId, UUID platformBuilderId) {
        UUID builderId = effectiveBuilderId(platformBuilderId);
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (TenantContext.getBuilderIdOrNull() != null) {
            assertBookingBuildingAccess(booking);
        }
        return booking;
    }

    private static UUID effectiveBuilderId(UUID platformBuilderId) {
        UUID tenant = TenantContext.getBuilderIdOrNull();
        if (tenant != null) {
            return tenant;
        }
        if (platformBuilderId == null) {
            throw new IllegalStateException("Tenant context missing");
        }
        return platformBuilderId;
    }

    private void assertBookingBuildingAccess(Booking booking) {
        if (booking.getFlat() != null
                && booking.getFlat().getBuilding() != null
                && !TenantContext.canAccessBuilding(booking.getFlat().getBuilding().getId())) {
            throw new ResourceNotFoundException("Booking not found");
        }
    }
}
