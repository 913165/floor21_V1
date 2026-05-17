package com.floor21.service;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.SlabScheduleSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.PaymentSlabTemplateRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPaymentSlabService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final PaymentSlabTemplateRepository paymentSlabTemplateRepository;

    @Transactional(readOnly = true)
    public List<Booking> listBookingsForSchedule(UUID buildingId) {
        UUID builderId = TenantContext.requireBuilderId();
        if (buildingId != null) {
            return bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        }
        return bookingRepository.findActiveForPaymentSchedule(builderId);
    }

    @Transactional(readOnly = true)
    public Booking getBookingForSchedule(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return bookingRepository
                .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listLines(UUID bookingId) {
        getBookingForSchedule(bookingId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    @Transactional(readOnly = true)
    public SlabScheduleSummary summarizeLines(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        BigDecimal agreed = ZERO;
        BigDecimal extra = ZERO;
        BigDecimal percent = ZERO;
        for (BookingPaymentSlab slab : listLines(bookingId)) {
            if (slab.getAgreedAmount() != null) {
                agreed = agreed.add(slab.getAgreedAmount());
            }
            if (slab.getExtraAmount() != null) {
                extra = extra.add(slab.getExtraAmount());
            }
            if (slab.getPercent() != null) {
                percent = percent.add(slab.getPercent());
            }
        }
        BigDecimal consideration = baseConsideration(booking);
        BigDecimal remaining =
                consideration != null && consideration.signum() > 0
                        ? consideration.subtract(agreed)
                        : null;
        return new SlabScheduleSummary(
                agreed, extra, agreed.add(extra), percent, consideration, remaining);
    }

    public BigDecimal baseConsideration(Booking booking) {
        if (booking.getConsiderationAmt() != null
                && booking.getConsiderationAmt().compareTo(BigDecimal.ZERO) > 0) {
            return booking.getConsiderationAmt();
        }
        if (booking.getFlat() != null && booking.getFlat().getBasePrice() != null) {
            return booking.getFlat().getBasePrice();
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal computeAgreedPortion(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) {
            return null;
        }
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void materializeFromTemplates(UUID bookingId, boolean replace) {
        Booking booking = getBookingForSchedule(bookingId);
        long existing = bookingPaymentSlabRepository.countByBooking_Id(bookingId);
        if (existing > 0 && !replace) {
            throw new IllegalArgumentException(
                    "This booking already has payment rows. Check “Replace existing rows” to rebuild from the current template, or edit the rows below.");
        }
        if (replace && existing > 0) {
            bookingPaymentSlabRepository.deleteByBooking_Id(bookingId);
        }
        UUID builderId = booking.getBuilder().getId();
        List<PaymentSlabTemplate> templates =
                paymentSlabTemplateRepository.findByBuilder_IdAndActiveTrueOrderBySortOrderAscIdAsc(builderId);
        if (templates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active payment milestones are defined. Add milestones under Payment milestones, then try again.");
        }
        Instant now = Instant.now();
        BigDecimal base = baseConsideration(booking);
        int order = 0;
        for (PaymentSlabTemplate t : templates) {
            BookingPaymentSlab row = new BookingPaymentSlab();
            row.setBooking(booking);
            row.setTemplate(t);
            row.setSortOrder(order++);
            row.setMilestoneLabel(t.getMilestoneLabel());
            row.setPercent(t.getSuggestedPercent());
            row.setExtraAmount(BigDecimal.ZERO);
            row.setDueDate(null);
            row.setAgreedAmount(computeAgreedPortion(base, row.getPercent()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(row);
        }
    }

    @Transactional
    public int saveLines(BookingPaymentSlabBatchForm form) {
        if (form.getBookingId() == null) {
            throw new IllegalArgumentException("Booking is required");
        }
        Booking booking = getBookingForSchedule(form.getBookingId());
        Instant now = Instant.now();
        if (form.getLines() == null || form.getLines().isEmpty()) {
            throw new IllegalArgumentException("No slab rows to save. Reload the booking and try again.");
        }
        int saved = 0;
        for (BookingPaymentSlabBatchForm.Line line : form.getLines()) {
            if (line.getId() == null) {
                continue;
            }
            BookingPaymentSlab entity =
                    bookingPaymentSlabRepository
                            .findById(line.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Payment row not found"));
            if (!entity.getBooking().getId().equals(booking.getId())) {
                throw new IllegalArgumentException("Invalid payment row for this booking");
            }
            entity.setDueDate(line.getDueDate());
            String label = line.getMilestoneLabel();
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Slab description cannot be empty.");
            }
            if (label.length() > 800) {
                throw new IllegalArgumentException("Slab description must be at most 800 characters.");
            }
            entity.setMilestoneLabel(label.trim());
            entity.setPercent(line.getPercent());
            entity.setExtraAmount(line.getExtraAmount() != null ? line.getExtraAmount() : BigDecimal.ZERO);
            entity.setAgreedAmount(line.getAgreedAmount());
            entity.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(entity);
            saved++;
        }
        if (saved == 0) {
            throw new IllegalArgumentException(
                    "No slab rows were saved. Reload the page and try again — if this persists, contact support.");
        }
        return saved;
    }
}
