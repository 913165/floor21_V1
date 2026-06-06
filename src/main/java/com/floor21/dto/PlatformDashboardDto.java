package com.floor21.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlatformDashboardDto(
        long totalBuilders,
        long activeBuilders,
        long inactiveBuilders,
        long totalBuildings,
        long totalFlats,
        long bookedFlats,
        long availableFlats,
        BigDecimal activeBookingValue,
        long bookingsThisMonth,
        List<AdminBuilderRow> recentBuilders) {}
