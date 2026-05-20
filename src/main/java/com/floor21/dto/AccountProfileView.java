package com.floor21.dto;

/** Signed-in account summary for the profile page. */
public record AccountProfileView(
        String displayName,
        String email,
        String roleLabel,
        String companyName,
        boolean builderAdmin,
        boolean platformAdmin,
        boolean vaultPinConfigured,
        boolean expensesPinConfigured,
        boolean vaultAccess) {}
