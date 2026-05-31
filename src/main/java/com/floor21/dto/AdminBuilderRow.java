package com.floor21.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminBuilderRow(
        UUID id,
        String companyName,
        String email,
        String city,
        boolean active,
        long buildingCount,
        UUID layoutId,
        long partnerCount,
        Instant lastLoginAt,
        Instant createdAt) {}
