package com.floor21.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardDto(
        boolean superAdmin,
        long totalFlats,
        long bookedFlats,
        long availableFlats,
        BigDecimal revenue,
        List<RecentBookingRow> recentBookings,
        List<BuildingPaymentSummaryRow> buildingPaymentSummaries) {

    public record RecentBookingRow(
            String bookingCode,
            String clientName,
            String clientInitials,
            String flatNumber,
            String buildingName,
            String status) {}

    public record BuildingPaymentSummaryRow(
            UUID buildingId,
            String buildingName,
            BigDecimal dueTillLatestSlab,
            BigDecimal totalReceived,
            BigDecimal duePending,
            long totalDemandLetters,
            long demandLettersIssued,
            long demandLettersRemaining,
            List<FlatDlPendingRow> flatDlPendingRows) {}

    public record FlatDlPendingRow(
            UUID bookingId,
            String flatNumber,
            String clientName,
            long dlPending) {}
}
