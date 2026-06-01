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
        Instant createdAt,
        Instant updatedAt) {

    /** Most recent of {@link #createdAt} and {@link #updatedAt}; used for list ordering. */
    public Instant lastActivityAt() {
        if (createdAt == null) {
            return updatedAt;
        }
        if (updatedAt == null) {
            return createdAt;
        }
        return updatedAt.isAfter(createdAt) ? updatedAt : createdAt;
    }
}
