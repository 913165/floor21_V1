package com.floor21.dto;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformUserView(
        UUID id,
        String fullName,
        String companyName,
        String email,
        String role,
        Boolean active,
        Instant createdAt,
        Instant lastLoginAt,
        List<String> buildingAccess,
        UUID builderId,
        String builderCompanyName,
        List<UUID> projectIds) {

    public static PlatformUserView from(
            User user, String projectNames, String role, List<String> buildingAccess, List<UUID> projectIds) {
        return new PlatformUserView(
                user.getId(),
                user.getFullName(),
                displayCompanyName(user),
                user.getEmail(),
                role,
                user.getActive(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                buildingAccess,
                null,
                projectNames,
                projectIds);
    }

    public static PlatformUserView from(User user, Builder builder, List<String> buildingAccess) {
        List<UUID> projectIds = builder != null ? List.of(builder.getId()) : List.of();
        return from(
                user,
                builder != null ? builder.getCompanyName() : "—",
                user.getRole(),
                buildingAccess,
                projectIds);
    }

    public static PlatformUserView unassigned(User user) {
        return new PlatformUserView(
                user.getId(),
                user.getFullName(),
                displayCompanyName(user),
                user.getEmail(),
                user.getRole(),
                user.getActive(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                List.of("Not assigned to a project yet"),
                null,
                "—",
                List.of());
    }

    private static String displayCompanyName(User user) {
        if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
            return user.getCompanyName().trim();
        }
        return "—";
    }
}
