package com.floor21.service;

import com.floor21.dto.DashboardDto;
import com.floor21.dto.DashboardDto.RecentBookingRow;
import com.floor21.entity.Booking;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FlatRepository flatRepository;
    private final BookingRepository bookingRepository;

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
            return new DashboardDto(true, total, booked, available, revenue, recent);
        }
        UUID builderId = TenantContext.requireBuilderId();
        long total = flatRepository.countByBuilder_Id(builderId);
        long booked = flatRepository.countByBuilder_IdAndStatus(builderId, "BOOKED");
        long available = flatRepository.countByBuilder_IdAndStatus(builderId, "AVAILABLE");
        BigDecimal revenue = bookingRepository.sumActiveConsideration(builderId);
        List<RecentBookingRow> recent =
                bookingRepository.findTop5ByBuilder_IdOrderByCreatedAtDesc(builderId).stream()
                        .map(this::toRow)
                        .toList();
        return new DashboardDto(false, total, booked, available, revenue, recent);
    }

    private RecentBookingRow toRow(Booking b) {
        return new RecentBookingRow(
                b.getBookingCode(),
                b.getClient().displayName(),
                b.getFlat().getFlatNumber(),
                b.getFlat().getBuilding().getBuildingName(),
                b.getStatus());
    }
}
