package com.floor21.service;

import com.floor21.dto.AdminBuilderRow;
import com.floor21.dto.DashboardDto.RecentBookingRow;
import com.floor21.dto.PlatformDashboardDto;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.PlatformAuditLog;
import com.floor21.entity.User;
import com.floor21.repository.BankRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BrokerRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.PlatformAuditLogRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    public static final int PROJECTS_DEFAULT_PAGE_SIZE = 25;
    public static final int PROJECTS_MAX_PAGE_SIZE = 100;

    private static final Set<String> PROJECTS_SORT_FIELDS =
            Set.of(
                    "companyName",
                    "city",
                    "buildingCount",
                    "active",
                    "createdAt",
                    "updatedAt",
                    "lastLoginAt",
                    "lastActivity");

    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final FlatRepository flatRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final SlabRepository slabRepository;
    private final ClientRepository clientRepository;
    private final BrokerRepository brokerRepository;
    private final BankRepository bankRepository;
    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final PlatformAuditLogRepository auditLogRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public PlatformDashboardDto loadDashboard() {
        long totalBuilders = builderRepository.countByPlatformAdminFalse();
        long activeBuilders = builderRepository.countByPlatformAdminFalseAndActiveTrue();
        long inactiveBuilders = builderRepository.countByPlatformAdminFalseAndActiveFalse();
        long totalBuildings = buildingRepository.countByBuilder_PlatformAdminFalse();
        long totalFlats = flatRepository.count();
        long booked = flatRepository.countAllByStatus("BOOKED");
        long available = flatRepository.countAllByStatus("AVAILABLE");
        BigDecimal revenue = bookingRepository.sumActiveConsiderationAll();
        Instant monthStart =
                YearMonth.now(ZoneId.systemDefault())
                        .atDay(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();
        long bookingsThisMonth = bookingRepository.countCreatedSince(monthStart);
        List<RecentBookingRow> recent =
                bookingRepository.findTop10ByOrderByCreatedAtDesc().stream().map(this::toBookingRow).toList();
        List<AdminBuilderRow> recentBuilders =
                builderRepository.findAllTenantsOrderByCompanyNameAsc().stream()
                        .map(this::toBuilderRow)
                        .sorted(Comparator.comparing(
                                AdminBuilderRow::lastActivityAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5)
                        .toList();
        return new PlatformDashboardDto(
                totalBuilders,
                activeBuilders,
                inactiveBuilders,
                totalBuildings,
                totalFlats,
                booked,
                available,
                revenue,
                bookingsThisMonth,
                recent,
                recentBuilders);
    }

    @Transactional(readOnly = true)
    public Page<AdminBuilderRow> listBuildersPage(int page, int size, String sort, String dir) {
        String sortKey = normalizeProjectsSort(sort);
        boolean ascending = normalizeProjectsSortAscending(sortKey, dir);
        int safeSize = Math.min(Math.max(size, 5), PROJECTS_MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        List<AdminBuilderRow> sorted = new ArrayList<>(loadAllBuilderRows());
        sorted.sort(comparatorForProjectsSort(sortKey, ascending));

        int total = sorted.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<AdminBuilderRow> slice = from < to ? sorted.subList(from, to) : List.of();
        return new PageImpl<>(slice, PageRequest.of(safePage, safeSize), total);
    }

    private List<AdminBuilderRow> loadAllBuilderRows() {
        return builderRepository.findAllTenantsOrderByCompanyNameAsc().stream()
                .map(this::toBuilderRow)
                .toList();
    }

    public static String normalizeProjectsSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "lastActivity";
        }
        String key = sort.trim();
        return PROJECTS_SORT_FIELDS.contains(key) ? key : "lastActivity";
    }

    public static boolean normalizeProjectsSortAscending(String sortKey, String dir) {
        if (dir != null && !dir.isBlank()) {
            return "asc".equalsIgnoreCase(dir.trim());
        }
        return switch (sortKey) {
            case "companyName", "city", "active" -> true;
            default -> false;
        };
    }

    private static Comparator<AdminBuilderRow> comparatorForProjectsSort(String sortKey, boolean ascending) {
        Comparator<AdminBuilderRow> comparator =
                switch (sortKey) {
                    case "companyName" ->
                            Comparator.comparing(
                                    AdminBuilderRow::companyName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "city" ->
                            Comparator.comparing(
                                    AdminBuilderRow::city,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "buildingCount" -> Comparator.comparingLong(AdminBuilderRow::buildingCount);
                    case "active" -> Comparator.comparing(AdminBuilderRow::active);
                    case "createdAt" ->
                            Comparator.comparing(
                                    AdminBuilderRow::createdAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    case "updatedAt" ->
                            Comparator.comparing(
                                    AdminBuilderRow::updatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    case "lastLoginAt" ->
                            Comparator.comparing(
                                    AdminBuilderRow::lastLoginAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    default ->
                            Comparator.comparing(
                                    AdminBuilderRow::lastActivityAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                };
        return ascending ? comparator : comparator.reversed();
    }

    @Transactional(readOnly = true)
    public List<PlatformAuditLog> recentAudit() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<RecentBookingRow> recentBookings() {
        return bookingRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toBookingRow).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminBuilderRow> recentlyLoggedInBuilders() {
        return builderRepository.findAllTenantsOrderByCompanyNameAsc().stream()
                .filter(b -> b.getLastLoginAt() != null)
                .sorted((a, b) -> b.getLastLoginAt().compareTo(a.getLastLoginAt()))
                .limit(10)
                .map(this::toBuilderRow)
                .toList();
    }

    @Transactional
    public void recordLogin(String email) {
        Instant now = Instant.now();
        builderRepository.findByEmailIgnoreCase(email).ifPresent(b -> {
            if (!b.isPlatformAdmin()) {
                b.setLastLoginAt(now);
                builderRepository.save(b);
            }
        });
        userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(email).ifPresent(u -> {
            u.setLastLoginAt(now);
            userRepository.save(u);
        });
    }

    @Transactional
    public void deactivateBuilder(UUID id, String adminEmail) {
        Builder builder =
                builderRepository
                        .findById(id)
                        .filter(b -> !b.isPlatformAdmin())
                        .orElseThrow(() -> new IllegalArgumentException("Builder not found."));
        builder.setActive(false);
        builder.setUpdatedAt(Instant.now());
        builderRepository.save(builder);
        auditService.log("BUILDER_DEACTIVATED", "builder", id.toString(), id, "Deactivated by " + adminEmail);
    }

    @Transactional
    public void deleteProject(UUID id, String adminEmail) {
        Builder builder =
                builderRepository
                        .findById(id)
                        .filter(b -> !b.isPlatformAdmin())
                        .orElseThrow(() -> new IllegalArgumentException("Project not found."));
        if (buildingRepository.countByBuilder_Id(id) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete a project that has buildings. Remove all buildings first.");
        }
        if (flatRepository.countByBuilder_Id(id) > 0) {
            throw new IllegalArgumentException("Cannot delete this project while flat inventory exists.");
        }
        long partnerCount = userProjectAssignmentRepository.countByBuilder_Id(id);
        if (partnerCount > 0) {
            throw new IllegalArgumentException(
                    "Remove all partners from this project first (Projects → Partners → Remove).");
        }
        if (!userRepository.findByBuilder_IdOrderByFullNameAsc(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Remove all partners from this project first (Projects → Partners → Remove).");
        }
        String projectName = builder.getCompanyName();
        slabRepository.deleteByBuilder_Id(id);
        clientRepository.deleteByBuilder_Id(id);
        brokerRepository.deleteByBuilder_Id(id);
        bankRepository.deleteByBuilder_Id(id);
        auditLogRepository.clearBuilderId(id);
        builderRepository.delete(builder);
        auditService.log(
                "BUILDER_DELETED",
                "builder",
                id.toString(),
                null,
                projectName + " deleted by " + adminEmail);
    }

    private AdminBuilderRow toBuilderRow(Builder b) {
        UUID layoutId =
                buildingRepository
                        .findFirstByBuilder_IdOrderByBuildingNameAsc(b.getId())
                        .map(Building::getId)
                        .orElse(null);
        return new AdminBuilderRow(
                b.getId(),
                b.getCompanyName(),
                b.getEmail(),
                b.getCity(),
                Boolean.TRUE.equals(b.getActive()),
                buildingRepository.countByBuilder_Id(b.getId()),
                layoutId,
                userProjectAssignmentRepository.countByBuilder_Id(b.getId()),
                b.getLastLoginAt(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }

    private RecentBookingRow toBookingRow(Booking b) {
        return new RecentBookingRow(
                b.getBookingCode(),
                b.getClient().displayName(),
                b.getFlat().getFlatNumber(),
                b.getFlat().getBuilding().getBuildingName(),
                b.getStatus());
    }
}
