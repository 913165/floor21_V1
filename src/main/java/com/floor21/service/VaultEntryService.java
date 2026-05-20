package com.floor21.service;

import com.floor21.dto.VaultBookingAmountForm;
import com.floor21.dto.VaultBookingAmountSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.Builder;
import com.floor21.entity.VaultEntry;
import com.floor21.entity.VaultEntryType;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.VaultEntryRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultEntryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final VaultEntryRepository vaultEntryRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final VaultBookingProfileService vaultBookingProfileService;

    @Transactional(readOnly = true)
    public List<VaultEntry> listIncomeForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.findByBooking_IdAndEntryTypeOrderByEntryDateDescCreatedAtDesc(
                bookingId, VaultEntryType.INCOME);
    }

    @Transactional(readOnly = true)
    public List<VaultEntry> listExpensesForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.findByBooking_IdAndEntryTypeOrderByEntryDateDescCreatedAtDesc(
                bookingId, VaultEntryType.EXPENSE);
    }

    @Transactional(readOnly = true)
    public VaultBookingAmountSummary summarizeAmounts(UUID bookingId) {
        requireBooking(bookingId);
        VaultBookingAmountForm deal = vaultBookingProfileService.getAmountForm(bookingId);
        BigDecimal register = zeroIfNull(deal.getRegisterValue());
        BigDecimal extra = zeroIfNull(deal.getExtraAmount());
        BigDecimal incomeTotal =
                zeroIfNull(
                        vaultEntryRepository.sumAmountByBookingIdAndEntryType(
                                bookingId, VaultEntryType.INCOME));
        BigDecimal expenseTotal =
                zeroIfNull(
                        vaultEntryRepository.sumAmountByBookingIdAndEntryType(
                                bookingId, VaultEntryType.EXPENSE));
        BigDecimal dealTotal = deal.getTotalConsideration();
        BigDecimal remaining = dealTotal != null ? dealTotal.subtract(incomeTotal) : null;
        return new VaultBookingAmountSummary(
                register,
                extra,
                register.add(extra),
                incomeTotal,
                incomeTotal,
                incomeTotal,
                expenseTotal,
                incomeTotal.subtract(expenseTotal),
                dealTotal,
                remaining);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    @Transactional(readOnly = true)
    public VaultEntry getIncomeForBooking(UUID id, UUID bookingId) {
        return getForBookingInternal(id, bookingId, VaultEntryType.INCOME);
    }

    @Transactional(readOnly = true)
    public VaultEntry getExpenseForBooking(UUID id, UUID bookingId) {
        return getForBookingInternal(id, bookingId, VaultEntryType.EXPENSE);
    }

    public VaultEntry newIncomeDraft(Booking booking) {
        return newDraft(booking, VaultEntryType.INCOME);
    }

    public VaultEntry newExpenseDraft(Booking booking) {
        return newDraft(booking, VaultEntryType.EXPENSE);
    }

    private static VaultEntry newDraft(Booking booking, String entryType) {
        VaultEntry entry = new VaultEntry();
        entry.setEntryType(entryType);
        entry.setEntryDate(LocalDate.now());
        entry.setClientName(ownerNameFrom(booking));
        entry.setFlatNumber(flatNumberFrom(booking));
        return entry;
    }

    @Transactional
    public VaultEntry saveIncome(UUID bookingId, VaultEntry form) {
        return saveEntry(bookingId, form, VaultEntryType.INCOME);
    }

    @Transactional
    public VaultEntry saveExpense(UUID bookingId, VaultEntry form) {
        return saveEntry(bookingId, form, VaultEntryType.EXPENSE);
    }

    private VaultEntry saveEntry(UUID bookingId, VaultEntry form, String entryType) {
        Booking booking = requireBooking(bookingId);
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();

        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (form.getEntryDate() == null) {
            throw new IllegalArgumentException("Date is required.");
        }

        VaultEntry entity;
        if (form.getId() == null) {
            entity = new VaultEntry();
            entity.setCreatedAt(now);
            entity.setEntryType(entryType);
        } else {
            entity = getForBookingInternal(form.getId(), bookingId, entryType);
        }

        entity.setBuilder(builder);
        entity.setBooking(booking);
        entity.setPaymentSlab(null);
        entity.setEntryType(entryType);
        entity.setClientName(ownerNameFrom(booking));
        entity.setFlatNumber(flatNumberFrom(booking));
        entity.setPaymentMode(trimToNull(form.getPaymentMode()));
        entity.setAmount(form.getAmount());
        entity.setEntryDate(form.getEntryDate());
        entity.setNotes(trimToNull(form.getNotes()));
        entity.setUpdatedAt(now);
        return vaultEntryRepository.save(entity);
    }

    @Transactional
    public void deleteIncome(UUID id, UUID bookingId) {
        deleteEntry(id, bookingId, VaultEntryType.INCOME);
    }

    @Transactional
    public void deleteExpense(UUID id, UUID bookingId) {
        deleteEntry(id, bookingId, VaultEntryType.EXPENSE);
    }

    private void deleteEntry(UUID id, UUID bookingId, String entryType) {
        VaultEntry entity = getForBookingInternal(id, bookingId, entryType);
        vaultEntryRepository.delete(entity);
    }

    private VaultEntry getForBookingInternal(UUID id, UUID bookingId, String entryType) {
        UUID builderId = TenantContext.requireBuilderId();
        VaultEntry entry =
                vaultEntryRepository
                        .findByIdAndBooking_IdAndBuilder_Id(id, bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Vault entry not found"));
        if (!entryType.equals(entry.getEntryType())) {
            throw new ResourceNotFoundException("Vault entry not found");
        }
        return entry;
    }

    private Booking requireBooking(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private static String ownerNameFrom(Booking booking) {
        if (booking.getClient() == null) {
            return "—";
        }
        return booking.getClient().displayName();
    }

    private static String flatNumberFrom(Booking booking) {
        if (booking.getFlat() == null || booking.getFlat().getFlatNumber() == null) {
            return "—";
        }
        return booking.getFlat().getFlatNumber();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
