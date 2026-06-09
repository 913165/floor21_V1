package com.floor21.dto;

import com.floor21.entity.User;
import java.util.UUID;

public record AssignableUserOption(UUID id, String fullName, String email) {

    public static AssignableUserOption from(User user) {
        return new AssignableUserOption(user.getId(), user.getFullName(), user.getEmail());
    }

    public String label() {
        return fullName + " (" + email + ")";
    }
}
