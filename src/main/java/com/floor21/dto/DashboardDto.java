package com.floor21.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
        boolean superAdmin,
        long totalFlats,
        long bookedFlats,
        long availableFlats,
        BigDecimal revenue,
        List<RecentBookingRow> recentBookings) {

    public record RecentBookingRow(
            String bookingCode,
            String clientName,
            String clientInitials,
            String flatNumber,
            String buildingName,
            String status) {}
}
