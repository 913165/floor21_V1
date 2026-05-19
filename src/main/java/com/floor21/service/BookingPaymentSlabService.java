package com.floor21.service;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.SlabPaymentSaveRequest;
import com.floor21.dto.SlabPaymentSaveResponse;
import com.floor21.dto.SlabPaymentSlice;
import com.floor21.dto.SlabScheduleLineView;
import com.floor21.dto.SlabScheduleSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.BookingSlabPayment;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BookingSlabPaymentRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final BookingSlabPaymentRepository bookingSlabPaymentRepository;
    private final PaymentSlabTemplateService paymentSlabTemplateService;

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

    /** Paid / balance per slab from payment rows entered on this schedule. */
    @Transactional(readOnly = true)
    public List<SlabScheduleLineView> listLineViews(UUID bookingId) {
        getBookingForSchedule(bookingId);
        List<BookingPaymentSlab> slabs = listLines(bookingId);
        Map<UUID, List<BookingSlabPayment>> paymentsBySlab = groupPaymentsBySlab(bookingId);

        List<SlabScheduleLineView> views = new ArrayList<>();
        for (BookingPaymentSlab slab : slabs) {
            BigDecimal due = slabDueAmount(slab);
            List<BookingSlabPayment> slabPayments =
                    paymentsBySlab.getOrDefault(slab.getId(), List.of());
            List<SlabPaymentSlice> slices = new ArrayList<>();
            BigDecimal paid = ZERO;
            for (BookingSlabPayment p : slabPayments) {
                paid = paid.add(p.getAmount());
                slices.add(
                        new SlabPaymentSlice(
                                p.getId(),
                                p.getPaymentDate(),
                                p.getAmount(),
                                p.getReference()));
            }
            BigDecimal balance = due.subtract(paid).max(ZERO);
            views.add(new SlabScheduleLineView(slab, due, paid, balance, List.copyOf(slices)));
        }
        return views;
    }

    private Map<UUID, List<BookingSlabPayment>> groupPaymentsBySlab(UUID bookingId) {
        Map<UUID, List<BookingSlabPayment>> map = new LinkedHashMap<>();
        for (BookingSlabPayment p :
                bookingSlabPaymentRepository.findByBookingIdOrderBySlabAndDate(bookingId)) {
            map.computeIfAbsent(p.getPaymentSlab().getId(), k -> new ArrayList<>()).add(p);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public SlabScheduleSummary summarizeLines(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        List<SlabScheduleLineView> views = listLineViews(bookingId);
        BigDecimal agreed = ZERO;
        BigDecimal extra = ZERO;
        BigDecimal percent = ZERO;
        BigDecimal paid = ZERO;
        BigDecimal balance = ZERO;
        for (SlabScheduleLineView line : views) {
            BookingPaymentSlab slab = line.slab();
            if (slab.getAgreedAmount() != null) {
                agreed = agreed.add(slab.getAgreedAmount());
            }
            if (slab.getExtraAmount() != null) {
                extra = extra.add(slab.getExtraAmount());
            }
            if (slab.getPercent() != null) {
                percent = percent.add(slab.getPercent());
            }
            paid = paid.add(line.paidAmount());
            balance = balance.add(line.balanceAmount());
        }
        BigDecimal consideration = baseConsideration(booking);
        BigDecimal remaining =
                consideration != null && consideration.signum() > 0
                        ? consideration.subtract(agreed)
                        : null;
        return new SlabScheduleSummary(
                agreed, extra, agreed.add(extra), percent, consideration, remaining, paid, balance);
    }

    private static BigDecimal slabDueAmount(BookingPaymentSlab slab) {
        return zeroIfNull(slab.getAgreedAmount()).add(zeroIfNull(slab.getExtraAmount()));
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v != null ? v : ZERO;
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
        if (base == null || percent == null || base.compareTo(ZERO) <= 0) {
            return null;
        }
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Creates slab rows from building milestones when the booking has none yet. */
    @Transactional
    public boolean materializeIfEmpty(UUID bookingId) {
        if (bookingPaymentSlabRepository.countByBooking_Id(bookingId) > 0) {
            return false;
        }
        materializeFromTemplates(bookingId, false);
        return true;
    }

    /** Recomputes agreed amounts from consideration × percent for all slabs on a booking. */
    @Transactional
    public void syncAgreedAmountsFromPercent(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        BigDecimal base = baseConsideration(booking);
        if (base.compareTo(ZERO) <= 0) {
            return;
        }
        Instant now = Instant.now();
        for (BookingPaymentSlab slab : listLines(bookingId)) {
            if (slab.getPercent() == null) {
                continue;
            }
            BigDecimal agreed = computeAgreedPortion(base, slab.getPercent());
            if (agreed == null) {
                continue;
            }
            slab.setAgreedAmount(agreed);
            slab.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(slab);
        }
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
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            throw new IllegalArgumentException("Booking has no building; cannot load payment milestones.");
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        List<PaymentSlabTemplate> templates = paymentSlabTemplateService.listActiveForBuilding(buildingId);
        if (templates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active payment milestones for this building. The Floor21 administrator must add milestones under Admin → Payment milestones for this building, then try again.");
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
            BigDecimal base = baseConsideration(booking);
            BigDecimal agreed = computeAgreedPortion(base, line.getPercent());
            entity.setAgreedAmount(agreed != null ? agreed : line.getAgreedAmount());
            entity.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(entity);
            savePaymentsForSlab(entity, line.getPayments(), now);
            saved++;
        }
        if (saved == 0) {
            throw new IllegalArgumentException(
                    "No slab rows were saved. Reload the page and try again — if this persists, contact support.");
        }
        return saved;
    }

    private void savePaymentsForSlab(
            BookingPaymentSlab slab, List<BookingPaymentSlabBatchForm.PaymentLine> lines, Instant now) {
        List<UUID> keepIds = new ArrayList<>();
        int sort = 0;
        if (lines != null) {
            for (BookingPaymentSlabBatchForm.PaymentLine line : lines) {
                if (line.getAmount() == null || line.getAmount().compareTo(ZERO) <= 0) {
                    continue;
                }
                if (line.getPaymentDate() == null) {
                    throw new IllegalArgumentException(
                            "Each payment on slab “"
                                    + slab.getMilestoneLabel()
                                    + "” needs a date.");
                }
                BookingSlabPayment entity;
                if (line.getId() != null) {
                    entity =
                            bookingSlabPaymentRepository
                                    .findById(line.getId())
                                    .orElseThrow(
                                            () -> new ResourceNotFoundException("Slab payment not found"));
                    if (!entity.getPaymentSlab().getId().equals(slab.getId())) {
                        throw new IllegalArgumentException("Invalid payment row for this slab");
                    }
                } else {
                    entity = new BookingSlabPayment();
                    entity.setPaymentSlab(slab);
                    entity.setCreatedAt(now);
                }
                entity.setPaymentDate(line.getPaymentDate());
                entity.setAmount(line.getAmount());
                entity.setReference(trimToNull(line.getReference()));
                entity.setSortOrder(sort++);
                entity.setUpdatedAt(now);
                bookingSlabPaymentRepository.save(entity);
                keepIds.add(entity.getId());
            }
        }
        if (keepIds.isEmpty()) {
            bookingSlabPaymentRepository.deleteByPaymentSlab_Id(slab.getId());
        } else {
            bookingSlabPaymentRepository.deleteByPaymentSlab_IdAndIdNotIn(slab.getId(), keepIds);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional
    public SlabPaymentSaveResponse saveSinglePayment(SlabPaymentSaveRequest request) {
        if (request.bookingId() == null || request.slabId() == null) {
            throw new IllegalArgumentException("Booking and slab are required.");
        }
        Booking booking = getBookingForSchedule(request.bookingId());
        BookingPaymentSlab slab = requireSlabForBooking(request.slabId(), booking.getId());
        if (request.amount() == null || request.amount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (request.paymentDate() == null) {
            throw new IllegalArgumentException("Payment date is required.");
        }
        Instant now = Instant.now();
        BookingSlabPayment entity = upsertSinglePayment(slab, request, now);
        return buildPaymentSaveResponse(booking.getId(), slab, entity.getId());
    }

    @Transactional
    public SlabPaymentSaveResponse deleteSinglePayment(UUID bookingId, UUID slabId, UUID paymentId) {
        if (bookingId == null || slabId == null || paymentId == null) {
            throw new IllegalArgumentException("Booking, slab, and payment are required.");
        }
        getBookingForSchedule(bookingId);
        requireSlabForBooking(slabId, bookingId);
        BookingSlabPayment entity =
                bookingSlabPaymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Slab payment not found"));
        if (!entity.getPaymentSlab().getId().equals(slabId)) {
            throw new IllegalArgumentException("Invalid payment row for this slab");
        }
        bookingSlabPaymentRepository.delete(entity);
        BookingPaymentSlab slab =
                bookingPaymentSlabRepository
                        .findById(slabId)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment row not found"));
        return buildPaymentSaveResponse(bookingId, slab, null);
    }

    private BookingPaymentSlab requireSlabForBooking(UUID slabId, UUID bookingId) {
        BookingPaymentSlab slab =
                bookingPaymentSlabRepository
                        .findById(slabId)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment row not found"));
        if (!slab.getBooking().getId().equals(bookingId)) {
            throw new IllegalArgumentException("Invalid payment row for this booking");
        }
        return slab;
    }

    private BookingSlabPayment upsertSinglePayment(
            BookingPaymentSlab slab, SlabPaymentSaveRequest request, Instant now) {
        BookingSlabPayment entity;
        if (request.id() != null) {
            entity =
                    bookingSlabPaymentRepository
                            .findById(request.id())
                            .orElseThrow(() -> new ResourceNotFoundException("Slab payment not found"));
            if (!entity.getPaymentSlab().getId().equals(slab.getId())) {
                throw new IllegalArgumentException("Invalid payment row for this slab");
            }
        } else {
            entity = new BookingSlabPayment();
            entity.setPaymentSlab(slab);
            entity.setCreatedAt(now);
            int nextSort =
                    bookingSlabPaymentRepository
                            .findByPaymentSlab_IdOrderByPaymentDateAscSortOrderAscIdAsc(slab.getId())
                            .size();
            entity.setSortOrder(nextSort);
        }
        entity.setPaymentDate(request.paymentDate());
        entity.setAmount(request.amount());
        entity.setReference(trimToNull(request.reference()));
        entity.setUpdatedAt(now);
        return bookingSlabPaymentRepository.save(entity);
    }

    private SlabPaymentSaveResponse buildPaymentSaveResponse(
            UUID bookingId, BookingPaymentSlab slab, UUID savedPaymentId) {
        BigDecimal due = slabDueAmount(slab);
        BigDecimal paid = ZERO;
        for (BookingSlabPayment p :
                bookingSlabPaymentRepository.findByPaymentSlab_IdOrderByPaymentDateAscSortOrderAscIdAsc(
                        slab.getId())) {
            paid = paid.add(p.getAmount());
        }
        BigDecimal balance = due.subtract(paid).max(ZERO);
        SlabScheduleSummary totals = summarizeLines(bookingId);
        return new SlabPaymentSaveResponse(
                savedPaymentId,
                due,
                paid,
                balance,
                totals.totalPaidAmount(),
                totals.totalBalanceAmount());
    }
}
