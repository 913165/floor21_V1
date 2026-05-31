package com.floor21.dto;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformUserView(
        UUID id,
        String fullName,
        String email,
        String role,
        Boolean active,
        Instant lastLoginAt,
        List<String> buildingAccess,
        UUID builderId,
        String builderCompanyName) {

    public static PlatformUserView from(
            User user, String projectNames, String role, List<String> buildingAccess) {
        return new PlatformUserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                role,
                user.getActive(),
                user.getLastLoginAt(),
                buildingAccess,
                null,
                projectNames);
    }

    public static PlatformUserView from(User user, Builder builder, List<String> buildingAccess) {
        return from(
                user,
                builder != null ? builder.getCompanyName() : "—",
                user.getRole(),
                buildingAccess);
    }

    public static PlatformUserView unassigned(User user) {
        return new PlatformUserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getActive(),
                user.getLastLoginAt(),
                List.of("Not assigned to a project yet"),
                null,
                "—");
    }
}
