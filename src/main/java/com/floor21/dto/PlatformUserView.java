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

    public static PlatformUserView from(User user, Builder builder, List<String> buildingAccess) {
        return new PlatformUserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getActive(),
                user.getLastLoginAt(),
                buildingAccess,
                builder.getId(),
                builder.getCompanyName());
    }
}
