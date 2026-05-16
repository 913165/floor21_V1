package com.floor21.service;

import com.floor21.dto.VaultBookingAmountForm;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Builder;
import com.floor21.entity.VaultBookingProfile;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.VaultBookingProfileRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultBookingProfileService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final VaultBookingProfileRepository profileRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public VaultBookingAmountForm getAmountForm(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        UUID builderId = TenantContext.requireBuilderId();

        BigDecimal defaultTotal = defaultTotalConsideration(booking);
        BigDecimal defaultRegister = sumSlabAgreed(bookingId);
        BigDecimal defaultExtra = defaultExtraAmount(defaultTotal, defaultRegister, bookingId);

        VaultBookingAmountForm form = new VaultBookingAmountForm();
        form.setBookingId(bookingId);
        profileRepository
                .findByBookingIdAndBuilder_Id(bookingId, builderId)
                .ifPresentOrElse(
                        p -> {
                            form.setRegisterValue(coalesce(p.getRegisterValue(), defaultRegister));
                            form.setExtraAmount(coalesce(p.getExtraAmount(), defaultExtra));
                            form.setTotalConsideration(computeTotal(form.getRegisterValue(), form.getExtraAmount()));
                        },
                        () -> {
                            form.setRegisterValue(defaultRegister);
                            form.setExtraAmount(defaultExtra);
                            form.setTotalConsideration(
                                    computeTotal(defaultRegister, defaultExtra, defaultTotal));
                        });
        return form;
    }

    @Transactional
    public void saveAmountForm(VaultBookingAmountForm form) {
        if (form.getBookingId() == null) {
            throw new IllegalArgumentException("Booking is required.");
        }
        UUID bookingId = form.getBookingId();
        requireBooking(bookingId);
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();

        BigDecimal register =
                normalizeAmount(form.getRegisterValue(), "Agreement / registered amount");
        BigDecimal extra = normalizeAmount(form.getExtraAmount(), "Extra / on-top amount");
        BigDecimal total = computeTotal(register, extra);

        VaultBookingProfile entity =
                profileRepository
                        .findByBookingIdAndBuilder_Id(bookingId, builderId)
                        .orElseGet(
                                () -> {
                                    VaultBookingProfile p = new VaultBookingProfile();
                                    p.setBookingId(bookingId);
                                    p.setBuilder(builder);
                                    return p;
                                });

        entity.setBuilder(builder);
        entity.setTotalConsideration(total);
        entity.setRegisterValue(register);
        entity.setExtraAmount(extra);
        entity.setUpdatedAt(Instant.now());
        profileRepository.save(entity);
    }

    private BigDecimal defaultTotalConsideration(Booking booking) {
        if (booking.getConsiderationAmt() != null
                && booking.getConsiderationAmt().compareTo(ZERO) > 0) {
            return booking.getConsiderationAmt();
        }
        if (booking.getFlat() != null && booking.getFlat().getBasePrice() != null) {
            return booking.getFlat().getBasePrice();
        }
        return null;
    }

    private BigDecimal sumSlabAgreed(UUID bookingId) {
        BigDecimal sum = ZERO;
        boolean any = false;
        for (BookingPaymentSlab slab :
                bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId)) {
            if (slab.getAgreedAmount() != null) {
                sum = sum.add(slab.getAgreedAmount());
                any = true;
            }
        }
        return any && sum.compareTo(ZERO) > 0 ? sum : null;
    }

    private BigDecimal sumSlabExtra(UUID bookingId) {
        BigDecimal sum = ZERO;
        boolean any = false;
        for (BookingPaymentSlab slab :
                bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId)) {
            if (slab.getExtraAmount() != null) {
                sum = sum.add(slab.getExtraAmount());
                any = true;
            }
        }
        return any && sum.compareTo(ZERO) > 0 ? sum : null;
    }

    private BigDecimal defaultExtraAmount(
            BigDecimal total, BigDecimal register, UUID bookingId) {
        if (total != null && register != null) {
            return total.subtract(register).max(ZERO);
        }
        return sumSlabExtra(bookingId);
    }

    /** Total deal = registered (official) + extra (on-top / unregistered). */
    private static BigDecimal computeTotal(BigDecimal register, BigDecimal extra) {
        return computeTotal(register, extra, null);
    }

    private static BigDecimal computeTotal(BigDecimal register, BigDecimal extra, BigDecimal fallback) {
        if (register != null && extra != null) {
            return register.add(extra);
        }
        if (register != null) {
            return register;
        }
        if (extra != null) {
            return extra;
        }
        return fallback;
    }

    private static BigDecimal coalesce(BigDecimal saved, BigDecimal fallback) {
        return saved != null ? saved : fallback;
    }

    private static BigDecimal normalizeAmount(BigDecimal value, String label) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return value;
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, TenantContext.requireBuilderId())
                .orElseThrow(
                        () ->
                                new com.floor21.exception.ResourceNotFoundException(
                                        "Booking not found"));
    }
}
