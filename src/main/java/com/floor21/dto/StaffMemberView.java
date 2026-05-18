package com.floor21.dto;

import com.floor21.entity.User;
import java.util.List;
import java.util.UUID;

public record StaffMemberView(
        UUID id,
        String fullName,
        String email,
        String role,
        Boolean active,
        java.time.Instant lastLoginAt,
        List<String> buildingAccess) {

    public static StaffMemberView from(User user, List<String> buildingAccess) {
        return new StaffMemberView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getActive(),
                user.getLastLoginAt(),
                buildingAccess);
    }
}
