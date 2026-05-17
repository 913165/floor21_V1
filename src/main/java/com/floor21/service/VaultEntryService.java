package com.floor21.service;

import com.floor21.dto.VaultBookingAmountSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Builder;
import com.floor21.entity.VaultEntry;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingPaymentSlabRepository;
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
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final VaultBookingProfileService vaultBookingProfileService;

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listSlabsForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    /** Vault payments for a booking — by date only, not tied to slab schedule. */
    @Transactional(readOnly = true)
    public List<VaultEntry> listPaymentsForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.findByBooking_IdOrderByEntryDateDescCreatedAtDesc(bookingId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.sumAmountByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public VaultBookingAmountSummary summarizeAmounts(UUID bookingId) {
        requireBooking(bookingId);
        BigDecimal totalFlat = ZERO;
        BigDecimal totalExtra = ZERO;
        for (BookingPaymentSlab slab : listSlabsForBooking(bookingId)) {
            totalFlat = totalFlat.add(zeroIfNull(slab.getAgreedAmount()));
            totalExtra = totalExtra.add(zeroIfNull(slab.getExtraAmount()));
        }
        BigDecimal vaultTotal = zeroIfNull(vaultEntryRepository.sumAmountByBookingId(bookingId));
        BigDecimal dealTotal = vaultBookingProfileService.getAmountForm(bookingId).getTotalConsideration();
        BigDecimal remaining =
                dealTotal != null ? dealTotal.subtract(vaultTotal) : null;
        return new VaultBookingAmountSummary(
                totalFlat,
                totalExtra,
                totalFlat.add(totalExtra),
                vaultTotal,
                vaultTotal,
                vaultTotal,
                dealTotal,
                remaining);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    @Transactional(readOnly = true)
    public VaultEntry getPaymentForBooking(UUID id, UUID bookingId) {
        return getForBookingInternal(id, bookingId);
    }

    public VaultEntry newPaymentDraft(Booking booking) {
        VaultEntry entry = new VaultEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setClientName(ownerNameFrom(booking));
        entry.setFlatNumber(flatNumberFrom(booking));
        return entry;
    }

    /** Date-based vault payment — never linked to slab schedule or booking amounts. */
    @Transactional
    public VaultEntry savePayment(UUID bookingId, VaultEntry form) {
        Booking booking = requireBooking(bookingId);
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();

        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (form.getEntryDate() == null) {
            throw new IllegalArgumentException("Date is required.");
        }

        VaultEntry entity;
        if (form.getId() == null) {
            entity = new VaultEntry();
            entity.setCreatedAt(now);
        } else {
            entity = getPaymentForBooking(form.getId(), bookingId);
        }

        entity.setBuilder(builder);
        entity.setBooking(booking);
        entity.setPaymentSlab(null);
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
    public void deletePayment(UUID id, UUID bookingId) {
        VaultEntry entity = getPaymentForBooking(id, bookingId);
        vaultEntryRepository.delete(entity);
    }

    private VaultEntry getForBookingInternal(UUID id, UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return vaultEntryRepository
                .findByIdAndBooking_IdAndBuilder_Id(id, bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault entry not found"));
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
