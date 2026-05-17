package com.floor21.service;

import com.floor21.dto.AdminBuilderRow;
import com.floor21.dto.DashboardDto.RecentBookingRow;
import com.floor21.dto.PlatformDashboardDto;
import com.floor21.entity.Booking;
import com.floor21.entity.Builder;
import com.floor21.entity.PlatformAuditLog;
import com.floor21.entity.User;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.PlatformAuditLogRepository;
import com.floor21.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final FlatRepository flatRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
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
                        .sorted((a, b) -> {
                            Instant ca = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.EPOCH;
                            Instant cb = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.EPOCH;
                            return cb.compareTo(ca);
                        })
                        .limit(5)
                        .map(this::toBuilderRow)
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
    public List<AdminBuilderRow> listBuilders() {
        return builderRepository.findAllTenantsOrderByCompanyNameAsc().stream().map(this::toBuilderRow).toList();
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

    private AdminBuilderRow toBuilderRow(Builder b) {
        return new AdminBuilderRow(
                b.getId(),
                b.getCompanyName(),
                b.getEmail(),
                b.getCity(),
                Boolean.TRUE.equals(b.getActive()),
                buildingRepository.countByBuilder_Id(b.getId()),
                b.getLastLoginAt(),
                b.getCreatedAt());
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
