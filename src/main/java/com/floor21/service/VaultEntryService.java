package com.floor21.service;

import com.floor21.dto.VaultEntryBatchForm;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultEntryService {

    private final VaultEntryRepository vaultEntryRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listSlabsForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    /** Extra vault-only rows (not linked to a slab line). Never written to slabs. */
    @Transactional(readOnly = true)
    public List<VaultEntry> listExtraForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.findByBooking_IdAndPaymentSlabIsNullOrderByEntryDateDescCreatedAtDesc(
                bookingId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForBooking(UUID bookingId) {
        requireBooking(bookingId);
        return vaultEntryRepository.sumAmountByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public VaultEntryBatchForm buildSaveForm(UUID bookingId) {
        requireBooking(bookingId);
        List<BookingPaymentSlab> slabs =
                bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
        Map<UUID, VaultEntry> vaultBySlabId = new HashMap<>();
        for (VaultEntry v :
                vaultEntryRepository.findByBooking_IdAndPaymentSlabIsNotNullOrderByPaymentSlab_SortOrderAscIdAsc(
                        bookingId)) {
            if (v.getPaymentSlab() != null) {
                vaultBySlabId.putIfAbsent(v.getPaymentSlab().getId(), v);
            }
        }

        VaultEntryBatchForm form = new VaultEntryBatchForm();
        form.setBookingId(bookingId);
        for (BookingPaymentSlab slab : slabs) {
            VaultEntryBatchForm.Line line = new VaultEntryBatchForm.Line();
            line.setPaymentSlabId(slab.getId());
            VaultEntry existing = vaultBySlabId.get(slab.getId());
            if (existing != null) {
                line.setId(existing.getId());
                line.setPaymentMode(existing.getPaymentMode());
                line.setAmount(existing.getAmount());
                line.setEntryDate(existing.getEntryDate());
                line.setNotes(existing.getNotes());
            }
            form.getLines().add(line);
        }
        return form;
    }

    /**
     * Persists vault payments tied to slab rows only. Does not read or write {@link BookingPaymentSlab}
     * amounts — slab schedule is maintained on the payment-schedule page.
     */
    @Transactional
    public void saveBatch(VaultEntryBatchForm form) {
        if (form.getBookingId() == null) {
            throw new IllegalArgumentException("Booking is required.");
        }
        Booking booking = requireBooking(form.getBookingId());
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();
        String flatNumber = flatNumberFrom(booking);
        String ownerName = ownerNameFrom(booking);

        if (form.getLines() == null) {
            return;
        }
        for (VaultEntryBatchForm.Line line : form.getLines()) {
            if (line.getPaymentSlabId() == null) {
                continue;
            }
            BookingPaymentSlab slab =
                    bookingPaymentSlabRepository
                            .findByIdAndBooking_Id(line.getPaymentSlabId(), booking.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Invalid slab row for this booking."));

            boolean hasPayment =
                    line.getAmount() != null && line.getAmount().compareTo(BigDecimal.ZERO) > 0;
            if (!hasPayment) {
                if (line.getId() != null) {
                    vaultEntryRepository
                            .findByIdAndBooking_IdAndBuilder_Id(line.getId(), booking.getId(), builderId)
                            .ifPresent(vaultEntryRepository::delete);
                }
                continue;
            }
            if (line.getEntryDate() == null) {
                throw new IllegalArgumentException(
                        "Date is required on every vault row with a payment amount.");
            }

            VaultEntry entity;
            if (line.getId() != null) {
                entity =
                        vaultEntryRepository
                                .findByIdAndBooking_IdAndBuilder_Id(
                                        line.getId(), booking.getId(), builderId)
                                .orElseThrow(() -> new ResourceNotFoundException("Vault entry not found"));
            } else {
                entity = new VaultEntry();
                entity.setCreatedAt(now);
            }

            entity.setBuilder(builder);
            entity.setBooking(booking);
            entity.setPaymentSlab(slab);
            entity.setClientName(ownerName);
            entity.setFlatNumber(flatNumber);
            entity.setPaymentMode(trimToNull(line.getPaymentMode()));
            entity.setAmount(line.getAmount());
            entity.setEntryDate(line.getEntryDate());
            entity.setNotes(trimToNull(line.getNotes()));
            entity.setUpdatedAt(now);
            vaultEntryRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public VaultEntry getExtraForBooking(UUID id, UUID bookingId) {
        VaultEntry entry = getForBookingInternal(id, bookingId);
        if (entry.getPaymentSlab() != null) {
            throw new ResourceNotFoundException("Vault entry not found");
        }
        return entry;
    }

    public VaultEntry newExtraDraft(Booking booking) {
        VaultEntry entry = new VaultEntry();
        entry.setEntryDate(LocalDate.now());
        entry.setClientName(ownerNameFrom(booking));
        entry.setFlatNumber(flatNumberFrom(booking));
        return entry;
    }

    /** Standalone vault row — never updates slab schedule. */
    @Transactional
    public VaultEntry saveExtra(UUID bookingId, VaultEntry form) {
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
            entity = getExtraForBooking(form.getId(), bookingId);
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
    public void deleteExtra(UUID id, UUID bookingId) {
        VaultEntry entity = getExtraForBooking(id, bookingId);
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
