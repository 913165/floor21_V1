package com.floor21.service;

import com.floor21.dto.BookingReceiptSummary;
import com.floor21.entity.Bank;
import com.floor21.entity.Booking;
import com.floor21.entity.Receipt;
import com.floor21.entity.User;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BankRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
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
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BigDecimal totalForBooking(UUID bookingId) {
        return receiptRepository.sumAmountForBooking(bookingId, TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public List<Receipt> listHistoryForBooking(UUID bookingId) {
        return receiptRepository.findByBooking_IdAndBuilder_IdOrderByReceiptSerialAscCreatedAtAsc(
                bookingId, TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public List<Receipt> listForTenant() {
        return receiptRepository.findForTenantList(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public BookingReceiptSummary summarizeBooking(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
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
        return receiptRepository
                .findFirstByBooking_IdAndBuilder_IdOrderByReceiptSerialDesc(
                        bookingId, TenantContext.requireBuilderId())
                .map(Receipt::getReceiptNumber)
                .filter(s -> s != null && !s.isBlank());
    }

    @Transactional(readOnly = true)
    public String previewNextReceiptNumber(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        int next = receiptRepository.findMaxReceiptSerialByBookingId(bookingId, builderId) + 1;
        return formatIncrementalReceiptNumber(next);
    }

    @Transactional(readOnly = true)
    public Receipt getForBooking(UUID receiptId, UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return receiptRepository
                .findByIdAndBooking_IdAndBuilder_Id(receiptId, bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
    }

    @Transactional(readOnly = true)
    public Receipt getForPrint(UUID receiptId, UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return receiptRepository
                .findByIdForPrintView(receiptId, bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
    }

    @Transactional
    public Receipt save(UUID bookingId, Receipt form) {
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
            entity.setEnteredByDisplay(resolveEnteredByDisplay(builderId));
            int nextSerial =
                    receiptRepository.findMaxReceiptSerialByBookingId(bookingId, builderId) + 1;
            entity.setReceiptSerial(nextSerial);
            entity.setReceiptNumber(formatIncrementalReceiptNumber(nextSerial));
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
                userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(email).filter(u -> builderId.equals(u.getBuilder().getId()));
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
}
