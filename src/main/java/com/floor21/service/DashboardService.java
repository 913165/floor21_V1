package com.floor21.service;

import com.floor21.dto.DashboardDto;
import com.floor21.dto.DashboardDto.BuildingPaymentSummaryRow;
import com.floor21.dto.DashboardDto.FlatDlPendingRow;
import com.floor21.dto.DashboardDto.RecentBookingRow;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.repository.BookingPaymentSlabRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final FlatRepository flatRepository;
    private final BookingRepository bookingRepository;
    private final BuildingRepository buildingRepository;
    private final BookingPaymentSlabRepository bookingPaymentSlabRepository;
    private final ReceiptRepository receiptRepository;

    @Transactional(readOnly = true)
    public DashboardDto load() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Floor21UserPrincipal p && p.isSuperAdmin()) {
            long total = flatRepository.count();
            long booked = flatRepository.countAllByStatus("BOOKED");
            long available = flatRepository.countAllByStatus("AVAILABLE");
            BigDecimal revenue = bookingRepository.sumActiveConsiderationAll();
            List<RecentBookingRow> recent =
                    bookingRepository.findTop10ByOrderByCreatedAtDesc().stream()
                            .map(this::toRow)
                            .toList();
            return new DashboardDto(true, total, booked, available, revenue, recent, List.of());
        }
        UUID builderId = TenantContext.requireBuilderId();
        Set<UUID> allowed = TenantContext.getAllowedBuildingIdsOrNull();
        long total;
        long booked;
        long available;
        if (allowed == null) {
            total = flatRepository.countByBuilder_Id(builderId);
            booked = flatRepository.countByBuilder_IdAndStatus(builderId, "BOOKED");
            available = flatRepository.countByBuilder_IdAndStatus(builderId, "AVAILABLE");
        } else {
            total = flatRepository.countByBuilder_IdAndBuilding_IdIn(builderId, allowed);
            booked = flatRepository.countByBuilder_IdAndStatusAndBuilding_IdIn(builderId, "BOOKED", allowed);
            available =
                    flatRepository.countByBuilder_IdAndStatusAndBuilding_IdIn(builderId, "AVAILABLE", allowed);
        }
        BigDecimal revenue = bookingRepository.sumActiveConsideration(builderId);
        List<BuildingPaymentSummaryRow> buildingSummaries =
                loadBuildingPaymentSummaries(builderId, allowed);
        return new DashboardDto(false, total, booked, available, revenue, List.of(), buildingSummaries);
    }

    private List<BuildingPaymentSummaryRow> loadBuildingPaymentSummaries(
            UUID builderId, Set<UUID> allowedBuildingIds) {
        List<Building> buildings =
                buildingRepository.findByBuilder_IdOrderByBuildingNameAsc(builderId).stream()
                        .filter(
                                b ->
                                        allowedBuildingIds == null
                                                || allowedBuildingIds.contains(b.getId()))
                        .toList();
        if (buildings.isEmpty()) {
            return List.of();
        }

        List<Booking> activeBookings =
                bookingRepository.findActiveWithBuildingByBuilder(builderId).stream()
                        .filter(
                                b ->
                                        b.getFlat() != null
                                                && b.getFlat().getBuilding() != null
                                                && (allowedBuildingIds == null
                                                        || allowedBuildingIds.contains(
                                                                b.getFlat().getBuilding().getId())))
                        .toList();

        List<UUID> bookingIds = activeBookings.stream().map(Booking::getId).toList();
        Map<UUID, BigDecimal> receivedByBooking =
                bookingIds.isEmpty()
                        ? Map.of()
                        : sumByBookingId(
                                receiptRepository.sumReceiptAmountGrouped(bookingIds, builderId));
        Map<UUID, List<BookingPaymentSlab>> slabsByBooking =
                bookingIds.isEmpty()
                        ? Map.of()
                        : groupSlabsByBooking(
                                bookingPaymentSlabRepository
                                        .findByBooking_IdInOrderByBooking_IdAscSortOrderAscIdAsc(
                                                bookingIds));

        Map<UUID, BuildingPaymentSummaryRow> rows = new LinkedHashMap<>();
        Map<UUID, List<FlatDlPendingRow>> flatsByBuilding = new LinkedHashMap<>();
        for (Building building : buildings) {
            rows.put(
                    building.getId(),
                    new BuildingPaymentSummaryRow(
                            building.getId(),
                            building.getBuildingName(),
                            ZERO,
                            ZERO,
                            ZERO,
                            0L,
                            0L,
                            0L,
                            List.of()));
            flatsByBuilding.put(building.getId(), new ArrayList<>());
        }

        for (Booking booking : activeBookings) {
            UUID buildingId = booking.getFlat().getBuilding().getId();
            BuildingPaymentSummaryRow current = rows.get(buildingId);
            if (current == null) {
                continue;
            }
            List<BookingPaymentSlab> bookingSlabs =
                    slabsByBooking.getOrDefault(booking.getId(), List.of());
            DashboardEligibleSlabStats slabStats =
                    DashboardEligibleSlabStats.fromSlabs(bookingSlabs, booking.getBookingDate());
            BigDecimal received = receivedByBooking.getOrDefault(booking.getId(), ZERO);
            BigDecimal duePending =
                    slabStats.dueAmount().subtract(received).max(ZERO);

            flatsByBuilding
                    .get(buildingId)
                    .add(
                            new FlatDlPendingRow(
                                    booking.getId(),
                                    booking.getFlat().getFlatNumber(),
                                    booking.getClient() != null
                                            ? booking.getClient().displayName()
                                            : "—",
                                    slabStats.dlPendingCount()));

            rows.put(
                    buildingId,
                    new BuildingPaymentSummaryRow(
                            current.buildingId(),
                            current.buildingName(),
                            current.dueTillLatestSlab().add(slabStats.dueAmount()),
                            current.totalReceived().add(received),
                            current.duePending().add(duePending),
                            current.totalDemandLetters() + slabStats.eligibleCount(),
                            current.demandLettersIssued() + slabStats.issuedCount(),
                            current.demandLettersRemaining() + slabStats.dlPendingCount(),
                            List.of()));
        }

        List<BuildingPaymentSummaryRow> result = new ArrayList<>();
        for (BuildingPaymentSummaryRow row : rows.values()) {
            List<FlatDlPendingRow> flats =
                    new ArrayList<>(flatsByBuilding.getOrDefault(row.buildingId(), List.of()));
            flats.sort(
                    Comparator.comparingLong(FlatDlPendingRow::dlPending)
                            .reversed()
                            .thenComparing(
                                    FlatDlPendingRow::flatNumber,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            result.add(
                    new BuildingPaymentSummaryRow(
                            row.buildingId(),
                            row.buildingName(),
                            row.dueTillLatestSlab(),
                            row.totalReceived(),
                            row.duePending(),
                            row.totalDemandLetters(),
                            row.demandLettersIssued(),
                            row.demandLettersRemaining(),
                            flats));
        }
        return result;
    }

    private static Map<UUID, List<BookingPaymentSlab>> groupSlabsByBooking(
            List<BookingPaymentSlab> slabs) {
        Map<UUID, List<BookingPaymentSlab>> grouped = new LinkedHashMap<>();
        for (BookingPaymentSlab slab : slabs) {
            if (slab.getBooking() == null || slab.getBooking().getId() == null) {
                continue;
            }
            grouped.computeIfAbsent(slab.getBooking().getId(), ignored -> new ArrayList<>())
                    .add(slab);
        }
        return grouped;
    }

    static BigDecimal dueTillLatestSlabForBooking(
            List<BookingPaymentSlab> slabsInOrder, LocalDate bookingDate) {
        return DashboardEligibleSlabStats.fromSlabs(slabsInOrder, bookingDate).dueAmount();
    }

    private static Map<UUID, BigDecimal> sumByBookingId(List<Object[]> rows) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            UUID bookingId = (UUID) row[0];
            BigDecimal amount = row[1] instanceof BigDecimal b ? b : ZERO;
            map.put(bookingId, amount);
        }
        return map;
    }

    private RecentBookingRow toRow(Booking b) {
        return new RecentBookingRow(
                b.getBookingCode(),
                b.getClient().displayName(),
                b.getClient().avatarInitials(),
                b.getFlat().getFlatNumber(),
                b.getFlat().getBuilding().getBuildingName(),
                b.getStatus());
    }
}
