package com.floor21.service;

import com.floor21.dto.BookingPaymentSlabBatchForm;
import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.dto.SlabPaymentSaveRequest;
import com.floor21.dto.SlabPaymentSaveResponse;
import com.floor21.dto.SlabPaymentSlice;
import com.floor21.dto.SlabScheduleDisplayLine;
import com.floor21.dto.SlabScheduleLineView;
import com.floor21.dto.SlabScheduleSummary;
import com.floor21.entity.Booking;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.BookingSlabPayment;
import com.floor21.entity.Building;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.entity.Slab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BookingSlabPaymentRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.SlabReceiptWaterfall;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingPaymentSlabService {

    public static final BigDecimal DEFAULT_INTEREST_RATE_PERCENT = new BigDecimal("15");

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BookingRepository bookingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final BookingSlabPaymentRepository bookingSlabPaymentRepository;
    private final ReceiptRepository receiptRepository;
    private final PaymentSlabTemplateService paymentSlabTemplateService;
    private final SlabRepository slabRepository;

    @Transactional(readOnly = true)
    public List<Booking> listBookingsForSchedule(UUID buildingId) {
        UUID builderId = TenantContext.requireBuilderId();
        if (buildingId != null) {
            if (!TenantContext.canAccessBuilding(buildingId)) {
                return List.of();
            }
            return bookingRepository.findActiveForPaymentScheduleByBuilding(builderId, buildingId);
        }
        List<Booking> all = bookingRepository.findActiveForPaymentSchedule(builderId);
        if (TenantContext.hasUnrestrictedBuildingAccess()) {
            return all;
        }
        return all.stream()
                .filter(
                        b ->
                                b.getFlat() != null
                                        && b.getFlat().getBuilding() != null
                                        && TenantContext.canAccessBuilding(
                                                b.getFlat().getBuilding().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Booking getBookingForSchedule(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return loadBookingForSchedule(bookingId, builderId);
    }

    @Transactional(readOnly = true)
    public Booking getBookingForScheduleReadOnly(UUID bookingId, UUID builderId) {
        return loadBookingForSchedule(bookingId, builderId);
    }

    private Booking loadBookingForSchedule(UUID bookingId, UUID builderId) {
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_IdForSchedule(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (TenantContext.getBuilderIdOrNull() != null
                && booking.getFlat() != null
                && booking.getFlat().getBuilding() != null
                && !TenantContext.canAccessBuilding(booking.getFlat().getBuilding().getId())) {
            throw new ResourceNotFoundException("Booking not found");
        }
        return booking;
    }

    /** Annual interest % for slab ledger; uses booking override or {@link #DEFAULT_INTEREST_RATE_PERCENT}. */
    public static BigDecimal effectiveInterestRatePercent(Booking booking) {
        if (booking != null && booking.getInterestRatePercent() != null) {
            return booking.getInterestRatePercent();
        }
        return DEFAULT_INTEREST_RATE_PERCENT;
    }

    @Transactional
    public void saveInterestRatePercent(UUID bookingId, BigDecimal ratePercent) {
        Booking booking = getBookingForSchedule(bookingId);
        applyInterestRatePercent(booking, ratePercent);
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);
    }

    private static void applyInterestRatePercent(Booking booking, BigDecimal ratePercent) {
        if (ratePercent == null) {
            throw new IllegalArgumentException("Interest rate is required.");
        }
        if (ratePercent.compareTo(ZERO) < 0 || ratePercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Interest rate must be between 0 and 100.");
        }
        booking.setInterestRatePercent(ratePercent);
    }

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listLines(UUID bookingId) {
        getBookingForSchedule(bookingId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listLinesReadOnly(UUID bookingId, UUID builderId) {
        getBookingForScheduleReadOnly(bookingId, builderId);
        return bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingSlabPayment> listPaymentsForBooking(UUID bookingId) {
        getBookingForSchedule(bookingId);
        return bookingSlabPaymentRepository.findByBookingIdWithReceipt(bookingId);
    }

    /** Milestone rows + agreed amounts for schedule; receipt slices are not persisted. */
    @Transactional
    public void prepareSlabMilestones(UUID bookingId) {
        getBookingForSchedule(bookingId);
        materializeIfEmpty(bookingId);
        ensureAllActiveMilestoneRows(bookingId);
        syncAgreedAmountsFromPercent(bookingId);
        deduplicateSlabRows(bookingId);
        consolidateOneSlabPerMilestoneLabel(bookingId);
        removeSlabsNotMatchingActiveTemplates(bookingId);
    }

    /** Paid / balance per slab computed from buyer receipts (waterfall, in memory). */
    @Transactional
    public List<SlabScheduleLineView> listLineViews(UUID bookingId) {
        prepareSlabMilestones(bookingId);
        List<BookingPaymentSlab> slabs = listSlabsForPaymentLedger(bookingId);
        UUID builderId = TenantContext.requireBuilderId();
        var receipts =
                receiptRepository.findActiveByBooking_IdOrderByReceiptDateAsc(bookingId, builderId);
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab =
                SlabReceiptWaterfall.allocate(slabs, receipts);

        List<SlabScheduleLineView> views = new ArrayList<>();
        for (BookingPaymentSlab slab : slabs) {
            BigDecimal due = slabDueAmount(slab);
            List<ReceiptSlabAllocationSlice> slabPayments =
                    bySlab.getOrDefault(slab.getId(), List.of());
            List<SlabPaymentSlice> slices = new ArrayList<>();
            BigDecimal paid = ZERO;
            for (ReceiptSlabAllocationSlice p : slabPayments) {
                paid = paid.add(p.amount());
                slices.add(
                        new SlabPaymentSlice(
                                null,
                                p.paymentDate(),
                                p.amount(),
                                p.chequeLabel() != null ? p.chequeLabel() : p.reference()));
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

    /** Totals for a booking; may sync slab rows via {@link #listLineViews} (not read-only). */
    @Transactional
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

    /** Ensures every active platform milestone for the booking's building has a slab row. */
    @Transactional
    public void ensureAllActiveMilestoneRows(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return;
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        List<PaymentSlabTemplate> templates =
                distinctActiveTemplates(
                        paymentSlabTemplateService.listActiveForBuilding(buildingId));
        if (templates.isEmpty()) {
            return;
        }
        linkOrphanSlabsToTemplates(bookingId);
        deduplicateSlabRows(bookingId);
        List<BookingPaymentSlab> existing = listLines(bookingId);
        Set<UUID> linkedTemplateIds = new HashSet<>();
        Set<String> existingLabels = new HashSet<>();
        for (BookingPaymentSlab slab : existing) {
            if (slab.getTemplate() != null && slab.getTemplate().getId() != null) {
                linkedTemplateIds.add(slab.getTemplate().getId());
            }
            existingLabels.add(normalizeMilestoneLabel(slab.getMilestoneLabel()));
        }
        BigDecimal base = baseConsideration(booking);
        Instant now = Instant.now();
        int order =
                existing.stream().mapToInt(BookingPaymentSlab::getSortOrder).max().orElse(-1) + 1;
        boolean added = false;
        for (PaymentSlabTemplate template : templates) {
            if (linkedTemplateIds.contains(template.getId())) {
                continue;
            }
            if (existingLabels.contains(normalizeMilestoneLabel(template.getMilestoneLabel()))) {
                continue;
            }
            BookingPaymentSlab row = new BookingPaymentSlab();
            row.setBooking(booking);
            row.setTemplate(template);
            row.setSortOrder(order++);
            row.setMilestoneLabel(template.getMilestoneLabel());
            row.setPercent(template.getSuggestedPercent());
            row.setExtraAmount(ZERO);
            row.setDueDate(null);
            row.setAgreedAmount(computeAgreedPortion(base, row.getPercent()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(row);
            added = true;
        }
        if (added) {
            syncAgreedAmountsFromPercent(bookingId);
        }
        linkOrphanSlabsToTemplates(bookingId);
        deduplicateSlabRows(bookingId);
    }

    /**
     * One row per active admin milestone for this building (order from templates). Legacy duplicate
     * booking rows with the same milestone are not returned.
     */
    @Transactional
    public List<BookingPaymentSlab> listUniqueSlabsForSchedule(UUID bookingId) {
        prepareSlabMilestones(bookingId);
        Booking booking = getBookingForSchedule(bookingId);
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return dedupeSlabsByLabel(listLines(bookingId));
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        return orderUniqueSlabsByTemplateLabels(
                listLines(bookingId), milestoneLabelsForBuilding(buildingId, false));
    }

    /** Read-only slab list for platform admin — does not sync or mutate booking milestone rows. */
    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listUniqueSlabsForScheduleReadOnly(UUID bookingId, UUID builderId) {
        Booking booking = getBookingForScheduleReadOnly(bookingId, builderId);
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return dedupeSlabsByLabel(listLinesReadOnly(bookingId, builderId));
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        return orderUniqueSlabsByTemplateLabels(
                listLinesReadOnly(bookingId, builderId),
                milestoneLabelsForBuilding(buildingId, true));
    }

    /**
     * Slabs shown on payment schedule / receipt waterfall: in template order, stopping at the first
     * milestone without a due date in Milestone setup (Clients).
     */
    @Transactional
    public List<BookingPaymentSlab> listSlabsForPaymentLedger(UUID bookingId) {
        return slabsThroughLastDueDate(listUniqueSlabsForSchedule(bookingId));
    }

    @Transactional(readOnly = true)
    public List<BookingPaymentSlab> listSlabsForPaymentLedgerReadOnly(UUID bookingId, UUID builderId) {
        return slabsThroughLastDueDate(listUniqueSlabsForScheduleReadOnly(bookingId, builderId));
    }

    static List<BookingPaymentSlab> slabsThroughLastDueDate(List<BookingPaymentSlab> slabs) {
        List<BookingPaymentSlab> result = new ArrayList<>();
        for (BookingPaymentSlab slab : slabs) {
            if (slab.getDueDate() == null) {
                break;
            }
            result.add(slab);
        }
        return result;
    }

    /**
     * Milestone setup (Clients) uses building {@code slabs} (Milestone Templates). Payment schedule
     * falls back to legacy {@code payment_slab_templates} when no building milestones exist.
     */
    private List<String> milestoneLabelsForBuilding(UUID buildingId, boolean readOnly) {
        List<Slab> milestoneSlabs =
                distinctActiveMilestoneSlabs(
                        slabRepository.findActiveMilestonesByBuilding_Id(buildingId));
        if (!milestoneSlabs.isEmpty()) {
            return milestoneSlabs.stream().map(BookingPaymentSlabService::resolveMilestoneLabel).toList();
        }
        List<PaymentSlabTemplate> templates =
                readOnly
                        ? distinctActiveTemplates(
                                paymentSlabTemplateService.listActiveForBuildingReadOnly(buildingId))
                        : distinctActiveTemplates(
                                paymentSlabTemplateService.listActiveForBuilding(buildingId));
        return templates.stream().map(PaymentSlabTemplate::getMilestoneLabel).toList();
    }

    private List<BookingPaymentSlab> orderUniqueSlabsByTemplateLabels(
            List<BookingPaymentSlab> allSlabs, List<String> templateLabelsInOrder) {
        Map<String, BookingPaymentSlab> byLabel = dedupeSlabsByLabelMap(allSlabs);
        if (templateLabelsInOrder.isEmpty()) {
            return dedupeSlabsByLabel(allSlabs);
        }
        List<BookingPaymentSlab> ordered = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();
        for (String templateLabel : templateLabelsInOrder) {
            String labelKey = normalizeMilestoneLabel(templateLabel);
            if (!seenLabels.add(labelKey)) {
                continue;
            }
            BookingPaymentSlab slab = byLabel.get(labelKey);
            if (slab != null) {
                ordered.add(slab);
            }
        }
        return ordered;
    }

    private static List<BookingPaymentSlab> dedupeSlabsByLabel(List<BookingPaymentSlab> allSlabs) {
        return new ArrayList<>(dedupeSlabsByLabelMap(allSlabs).values());
    }

    private static Map<String, BookingPaymentSlab> dedupeSlabsByLabelMap(List<BookingPaymentSlab> allSlabs) {
        Map<String, BookingPaymentSlab> byLabel = new LinkedHashMap<>();
        for (BookingPaymentSlab slab : allSlabs) {
            byLabel.merge(
                    normalizeMilestoneLabel(slab.getMilestoneLabel()),
                    slab,
                    BookingPaymentSlabService::chooseKeeperSlabPair);
        }
        return byLabel;
    }

    private static BookingPaymentSlab chooseKeeperSlabPair(BookingPaymentSlab a, BookingPaymentSlab b) {
        return chooseKeeperSlab(List.of(a, b));
    }

    /** Multiple active DB templates can share the same label (e.g. after migrations); keep one per label. */
    private static List<PaymentSlabTemplate> distinctActiveTemplates(List<PaymentSlabTemplate> templates) {
        Map<String, PaymentSlabTemplate> byLabel = new LinkedHashMap<>();
        for (PaymentSlabTemplate template : templates) {
            String key = normalizeMilestoneLabel(template.getMilestoneLabel());
            byLabel.merge(
                    key,
                    template,
                    (a, b) -> {
                        int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                        int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                        return orderA <= orderB ? a : b;
                    });
        }
        return new ArrayList<>(byLabel.values());
    }

    /** Ensures at most one booking_payment_slabs row per milestone label. */
    @Transactional
    public void consolidateOneSlabPerMilestoneLabel(UUID bookingId) {
        Map<String, BookingPaymentSlab> keepers = new LinkedHashMap<>();
        for (BookingPaymentSlab slab : listLines(bookingId)) {
            keepers.merge(normalizeMilestoneLabel(slab.getMilestoneLabel()), slab, this::pickPreferredSlab);
        }
        for (BookingPaymentSlab slab : new ArrayList<>(listLines(bookingId))) {
            String key = normalizeMilestoneLabel(slab.getMilestoneLabel());
            BookingPaymentSlab keeper = keepers.get(key);
            if (keeper != null && !keeper.getId().equals(slab.getId())) {
                bookingSlabPaymentRepository.deleteByPaymentSlab_Id(slab.getId());
                bookingPaymentSlabRepository.delete(slab);
            }
        }
    }

    /** Drops booking slab rows that do not match any active platform milestone for the building. */
    @Transactional
    public void removeSlabsNotMatchingActiveTemplates(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return;
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        List<PaymentSlabTemplate> templates =
                distinctActiveTemplates(
                        paymentSlabTemplateService.listActiveForBuilding(buildingId));
        Set<UUID> activeTemplateIds = new HashSet<>();
        Set<String> activeLabels = new HashSet<>();
        for (PaymentSlabTemplate template : templates) {
            activeTemplateIds.add(template.getId());
            activeLabels.add(normalizeMilestoneLabel(template.getMilestoneLabel()));
        }
        for (Slab milestoneSlab : listActiveBuildingMilestoneSlabs(buildingId)) {
            activeLabels.add(normalizeMilestoneLabel(resolveMilestoneLabel(milestoneSlab)));
        }
        for (BookingPaymentSlab slab : new ArrayList<>(listLines(bookingId))) {
            if (slab.getTemplate() != null
                    && slab.getTemplate().getId() != null
                    && activeTemplateIds.contains(slab.getTemplate().getId())) {
                continue;
            }
            if (activeLabels.contains(normalizeMilestoneLabel(slab.getMilestoneLabel()))) {
                continue;
            }
            bookingSlabPaymentRepository.deleteByPaymentSlab_Id(slab.getId());
            bookingPaymentSlabRepository.delete(slab);
        }
    }

    private BookingPaymentSlab pickPreferredSlab(BookingPaymentSlab a, BookingPaymentSlab b) {
        return chooseKeeperSlab(List.of(a, b));
    }

    /**
     * Links legacy slab rows (no template) to platform milestones when the label matches, so a second
     * row is not created for the same milestone.
     */
    @Transactional
    public void linkOrphanSlabsToTemplates(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return;
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        List<PaymentSlabTemplate> templates =
                distinctActiveTemplates(
                        paymentSlabTemplateService.listActiveForBuilding(buildingId));
        if (templates.isEmpty()) {
            return;
        }
        List<BookingPaymentSlab> slabs = listLines(bookingId);
        Instant now = Instant.now();
        boolean changed = false;
        for (PaymentSlabTemplate template : templates) {
            boolean templateUsed =
                    slabs.stream()
                            .anyMatch(
                                    s ->
                                            s.getTemplate() != null
                                                    && template.getId().equals(s.getTemplate().getId()));
            if (templateUsed) {
                continue;
            }
            String templateLabel = normalizeMilestoneLabel(template.getMilestoneLabel());
            for (BookingPaymentSlab slab : slabs) {
                if (slab.getTemplate() != null) {
                    continue;
                }
                if (!templateLabel.equals(normalizeMilestoneLabel(slab.getMilestoneLabel()))) {
                    continue;
                }
                slab.setTemplate(template);
                if (slab.getPercent() == null) {
                    slab.setPercent(template.getSuggestedPercent());
                }
                slab.setUpdatedAt(now);
                bookingPaymentSlabRepository.save(slab);
                changed = true;
                break;
            }
        }
        if (changed) {
            syncAgreedAmountsFromPercent(bookingId);
        }
    }

    /**
     * Merges duplicate slab rows (same template or same milestone label). Receipt slices on removed
     * slabs are moved to the kept row.
     */
    @Transactional
    public void deduplicateSlabRows(UUID bookingId) {
        List<BookingPaymentSlab> slabs = new ArrayList<>(listLines(bookingId));
        if (slabs.size() <= 1) {
            return;
        }
        Map<String, List<BookingPaymentSlab>> groups = new LinkedHashMap<>();
        for (BookingPaymentSlab slab : slabs) {
            groups.computeIfAbsent(dedupeGroupKey(slab), k -> new ArrayList<>()).add(slab);
        }
        for (List<BookingPaymentSlab> group : groups.values()) {
            if (group.size() <= 1) {
                continue;
            }
            BookingPaymentSlab keeper = chooseKeeperSlab(group);
            for (BookingPaymentSlab duplicate : group) {
                if (duplicate.getId().equals(keeper.getId())) {
                    continue;
                }
                bookingSlabPaymentRepository.deleteByPaymentSlab_Id(duplicate.getId());
                bookingPaymentSlabRepository.delete(duplicate);
            }
            mergeKeeperMetadata(keeper, group);
            bookingPaymentSlabRepository.save(keeper);
        }
    }

    /** Same milestone label = same slab, even if one row has template_id and another is legacy. */
    private static String dedupeGroupKey(BookingPaymentSlab slab) {
        return normalizeMilestoneLabel(slab.getMilestoneLabel());
    }

    private static BookingPaymentSlab chooseKeeperSlab(List<BookingPaymentSlab> group) {
        return group.stream()
                .min(
                        Comparator.comparing((BookingPaymentSlab s) -> s.getTemplate() == null ? 1 : 0)
                                .thenComparing(s -> s.getDueDate() == null ? 1 : 0)
                                .thenComparing(BookingPaymentSlab::getSortOrder)
                                .thenComparing(BookingPaymentSlab::getId))
                .orElse(group.getFirst());
    }

    private static void mergeKeeperMetadata(BookingPaymentSlab keeper, List<BookingPaymentSlab> group) {
        for (BookingPaymentSlab other : group) {
            if (other.getId().equals(keeper.getId())) {
                continue;
            }
            if (keeper.getDueDate() == null && other.getDueDate() != null) {
                keeper.setDueDate(other.getDueDate());
            }
            if (keeper.getTemplate() == null && other.getTemplate() != null) {
                keeper.setTemplate(other.getTemplate());
            }
            if (keeper.getPercent() == null && other.getPercent() != null) {
                keeper.setPercent(other.getPercent());
            }
        }
    }

    static String normalizeMilestoneLabel(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase().replaceAll("\\s+", " ");
        // Legacy rows often differ only by "this" (e.g. "execution of this Agreement").
        return normalized.replace(" of this agreement", " of agreement");
    }

    /**
     * Read-only schedule rows: one line per active platform milestone, merged with booking data.
     * Missing dates and amounts show as null for the UI to render as an em dash.
     */
    @Transactional(readOnly = true)
    public List<SlabScheduleDisplayLine> buildScheduleDisplay(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        UUID buildingId = buildingIdFromBooking(booking);
        if (buildingId == null) {
            return List.of();
        }
        List<PaymentSlabTemplate> templates =
                distinctActiveTemplates(
                        paymentSlabTemplateService.listActiveForBuilding(buildingId));
        if (templates.isEmpty()) {
            return List.of();
        }
        Map<UUID, SlabScheduleLineView> byTemplateId = new LinkedHashMap<>();
        List<SlabScheduleLineView> allViews = listLineViews(bookingId);
        for (SlabScheduleLineView view : allViews) {
            if (view.slab().getTemplate() != null && view.slab().getTemplate().getId() != null) {
                byTemplateId.put(view.slab().getTemplate().getId(), view);
            }
        }
        BigDecimal base = baseConsideration(booking);
        List<SlabScheduleDisplayLine> rows = new ArrayList<>();
        int serial = 1;
        Set<UUID> coveredSlabIds = new HashSet<>();
        for (PaymentSlabTemplate template : templates) {
            SlabScheduleLineView view = byTemplateId.get(template.getId());
            if (view != null) {
                BookingPaymentSlab slab = view.slab();
                coveredSlabIds.add(slab.getId());
                BigDecimal percent =
                        slab.getPercent() != null ? slab.getPercent() : template.getSuggestedPercent();
                rows.add(
                        new SlabScheduleDisplayLine(
                                serial++,
                                slab.getMilestoneLabel(),
                                percent,
                                slab.getDueDate(),
                                slab.getAgreedAmount(),
                                slab.getExtraAmount(),
                                view.paidAmount(),
                                view.balanceAmount()));
            } else {
                BigDecimal percent = template.getSuggestedPercent();
                BigDecimal agreed = computeAgreedPortion(base, percent);
                BigDecimal extra = ZERO;
                BigDecimal balance =
                        agreed != null ? agreed.add(extra) : null;
                rows.add(
                        new SlabScheduleDisplayLine(
                                serial++,
                                template.getMilestoneLabel(),
                                percent,
                                null,
                                agreed,
                                extra,
                                ZERO,
                                balance));
            }
        }
        for (SlabScheduleLineView view : allViews) {
            if (coveredSlabIds.contains(view.slab().getId())) {
                continue;
            }
            BookingPaymentSlab slab = view.slab();
            coveredSlabIds.add(slab.getId());
            rows.add(
                    new SlabScheduleDisplayLine(
                            serial++,
                            slab.getMilestoneLabel(),
                            slab.getPercent(),
                            slab.getDueDate(),
                            slab.getAgreedAmount(),
                            slab.getExtraAmount(),
                            view.paidAmount(),
                            view.balanceAmount()));
        }
        return rows;
    }

    private static UUID buildingIdFromBooking(Booking booking) {
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            return null;
        }
        return booking.getFlat().getBuilding().getId();
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

    /**
     * Milestone setup (Clients): load names and percents from the building's Milestone Templates
     * ({@code slabs} table), then calculate agreed amounts from the booking consideration.
     */
    @Transactional
    public boolean prepareClientMilestoneSetup(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        UUID buildingId = buildingIdFromBooking(booking);
        if (buildingId == null) {
            return false;
        }
        List<Slab> templates = listActiveBuildingMilestoneSlabs(buildingId);
        long existing = bookingPaymentSlabRepository.countByBooking_Id(bookingId);
        if (existing == 0) {
            if (!templates.isEmpty()) {
                createBookingSlabsFromMilestoneSlabs(booking, templates);
            } else {
                try {
                    materializeFromPaymentTemplates(booking, false);
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            }
            syncAgreedAmountsFromPercent(bookingId);
            deduplicateSlabRows(bookingId);
            consolidateOneSlabPerMilestoneLabel(bookingId);
            backfillMissingDueDatesFromTemplate(bookingId);
            return true;
        }
        deduplicateSlabRows(bookingId);
        consolidateOneSlabPerMilestoneLabel(bookingId);
        backfillMissingDueDatesFromTemplate(bookingId);
        return false;
    }

    /**
     * Client milestone setup display: saved row date, else per-milestone template date, else building
     * common template date — never earlier than the booking date.
     */
    @Transactional(readOnly = true)
    public LocalDate resolveClientSlabDueDate(
            BookingPaymentSlab slab, Building building, Booking booking) {
        LocalDate raw;
        if (slab.getDueDate() != null) {
            raw = slab.getDueDate();
        } else if (building != null && building.getId() != null) {
            Slab template = findMilestoneTemplateForLabel(building.getId(), slab.getMilestoneLabel());
            raw = resolveDueDateFromTemplate(template, building);
        } else {
            raw = null;
        }
        LocalDate bookingDate = booking != null ? booking.getBookingDate() : null;
        return clampDueDateToBookingDate(raw, bookingDate);
    }

    /** Fills or corrects client slab dates from templates; clamps past dates to booking date. */
    @Transactional
    public int backfillMissingDueDatesFromTemplate(UUID bookingId) {
        Booking booking = getBookingForSchedule(bookingId);
        Building building = booking.getFlat() != null ? booking.getFlat().getBuilding() : null;
        if (building == null || building.getId() == null) {
            return 0;
        }
        Instant now = Instant.now();
        int updated = 0;
        for (BookingPaymentSlab slab : listLines(bookingId)) {
            LocalDate resolved = resolveClientSlabDueDate(slab, building, booking);
            if (resolved != null && !resolved.equals(slab.getDueDate())) {
                slab.setDueDate(resolved);
                slab.setUpdatedAt(now);
                bookingPaymentSlabRepository.save(slab);
                updated++;
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public boolean hasBuildingMilestoneTemplates(UUID buildingId) {
        if (buildingId == null) {
            return false;
        }
        return !listActiveBuildingMilestoneSlabs(buildingId).isEmpty();
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
        if (booking.getFlat() == null || booking.getFlat().getBuilding() == null) {
            throw new IllegalArgumentException("Booking has no building; cannot load payment milestones.");
        }
        UUID buildingId = booking.getFlat().getBuilding().getId();
        List<Slab> milestoneSlabs = listActiveBuildingMilestoneSlabs(buildingId);
        if (!milestoneSlabs.isEmpty()) {
            materializeFromMilestoneSlabs(booking, milestoneSlabs, replace);
            return;
        }
        materializeFromPaymentTemplates(booking, replace);
    }

    /** Rebuilds this booking's milestone rows from the current building template. */
    @Transactional
    public void resetTemplateForBooking(UUID bookingId) {
        materializeFromTemplates(bookingId, true);
    }

    private void materializeFromMilestoneSlabs(Booking booking, List<Slab> templates, boolean replace) {
        UUID bookingId = booking.getId();
        long existing = bookingPaymentSlabRepository.countByBooking_Id(bookingId);
        if (existing > 0 && !replace) {
            throw new IllegalArgumentException(
                    "This booking already has payment rows. Check “Replace existing rows” to rebuild from the current template, or edit the rows below.");
        }
        if (replace && existing > 0) {
            bookingPaymentSlabRepository.deleteByBooking_Id(bookingId);
        }
        createBookingSlabsFromMilestoneSlabs(booking, distinctActiveMilestoneSlabs(templates));
        syncAgreedAmountsFromPercent(bookingId);
        deduplicateSlabRows(bookingId);
        consolidateOneSlabPerMilestoneLabel(bookingId);
    }

    private void materializeFromPaymentTemplates(Booking booking, boolean replace) {
        UUID bookingId = booking.getId();
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
        List<PaymentSlabTemplate> templates =
                distinctActiveTemplates(
                        paymentSlabTemplateService.listActiveForBuilding(buildingId));
        if (templates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No active Milestone Templates for this building. Add milestones under Milestone Templates for this building, then try again.");
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

    private List<Slab> listActiveBuildingMilestoneSlabs(UUID buildingId) {
        return distinctActiveMilestoneSlabs(slabRepository.findActiveMilestonesByBuilding_Id(buildingId));
    }

    private Map<String, Slab> milestoneTemplatesByLabelMap(UUID buildingId) {
        Map<String, Slab> byLabel = new LinkedHashMap<>();
        for (Slab template : listActiveBuildingMilestoneSlabs(buildingId)) {
            byLabel.put(normalizeMilestoneLabel(resolveMilestoneLabel(template)), template);
        }
        return byLabel;
    }

    private Slab findMilestoneTemplateForLabel(UUID buildingId, String milestoneLabel) {
        return milestoneTemplatesByLabelMap(buildingId).get(normalizeMilestoneLabel(milestoneLabel));
    }

    /** Multiple slab rows can share the same label after imports; keep one per label for schedules. */
    private static List<Slab> distinctActiveMilestoneSlabs(List<Slab> templates) {
        Map<String, Slab> byLabel = new LinkedHashMap<>();
        for (Slab template : templates) {
            String key = normalizeMilestoneLabel(resolveMilestoneLabel(template));
            byLabel.merge(
                    key,
                    template,
                    (a, b) -> {
                        int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
                        int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
                        return orderA <= orderB ? a : b;
                    });
        }
        return new ArrayList<>(byLabel.values());
    }

    private void createBookingSlabsFromMilestoneSlabs(Booking booking, List<Slab> templates) {
        Instant now = Instant.now();
        BigDecimal base = baseConsideration(booking);
        Building building = booking.getFlat() != null ? booking.getFlat().getBuilding() : null;
        int order = 0;
        for (Slab template : distinctActiveMilestoneSlabs(templates)) {
            BookingPaymentSlab row = new BookingPaymentSlab();
            row.setBooking(booking);
            row.setTemplate(null);
            row.setSortOrder(order++);
            row.setMilestoneLabel(resolveMilestoneLabel(template));
            row.setPercent(template.getSuggestedPercent());
            row.setExtraAmount(ZERO);
            row.setDueDate(
                    clampDueDateToBookingDate(
                            resolveDueDateFromTemplate(template, building), booking.getBookingDate()));
            row.setAgreedAmount(computeAgreedPortion(base, row.getPercent()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(row);
        }
    }

    private static LocalDate resolveDueDateFromTemplate(Slab template, Building building) {
        if (template != null && template.getDefaultDueDate() != null) {
            return template.getDefaultDueDate();
        }
        if (building != null && building.getMilestoneTemplateDueDate() != null) {
            return building.getMilestoneTemplateDueDate();
        }
        return null;
    }

    /** Client slab due dates cannot be before the flat booking date. */
    static LocalDate clampDueDateToBookingDate(LocalDate dueDate, LocalDate bookingDate) {
        if (dueDate == null) {
            return null;
        }
        if (bookingDate == null || !dueDate.isBefore(bookingDate)) {
            return dueDate;
        }
        return bookingDate;
    }

    @Transactional
    public void applyTemplateDueDateToBooking(UUID bookingId, LocalDate dueDate, boolean overwriteExisting) {
        if (dueDate == null) {
            return;
        }
        Booking booking = getBookingForSchedule(bookingId);
        LocalDate clamped = clampDueDateToBookingDate(dueDate, booking.getBookingDate());
        Instant now = Instant.now();
        for (BookingPaymentSlab slab :
                bookingPaymentSlabRepository.findByBooking_IdOrderBySortOrderAscIdAsc(bookingId)) {
            if (!overwriteExisting && slab.getDueDate() != null) {
                continue;
            }
            slab.setDueDate(clamped);
            slab.setUpdatedAt(now);
            bookingPaymentSlabRepository.save(slab);
        }
    }

    private static String resolveMilestoneLabel(Slab slab) {
        if (slab.getSlabName() != null && !slab.getSlabName().isBlank()) {
            return slab.getSlabName().trim();
        }
        if (slab.getDescription() != null && !slab.getDescription().isBlank()) {
            return slab.getDescription().trim();
        }
        return "Milestone";
    }

    @Transactional
    public int saveLines(BookingPaymentSlabBatchForm form) {
        if (form.getBookingId() == null) {
            throw new IllegalArgumentException("Booking is required");
        }
        Booking booking = getBookingForSchedule(form.getBookingId());
        if (form.getInterestRatePercent() != null) {
            applyInterestRatePercent(booking, form.getInterestRatePercent());
            booking.setUpdatedAt(Instant.now());
            bookingRepository.save(booking);
        }
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
            entity.setDueDate(clampDueDateToBookingDate(line.getDueDate(), booking.getBookingDate()));
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
            if (line.getAgreedAmount() != null) {
                entity.setAgreedAmount(line.getAgreedAmount());
            } else {
                BigDecimal base = baseConsideration(booking);
                BigDecimal agreed = computeAgreedPortion(base, line.getPercent());
                entity.setAgreedAmount(agreed);
            }
            entity.setUpdatedAt(now);
            bookingPaymentSlabRepository.saveAndFlush(entity);
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
