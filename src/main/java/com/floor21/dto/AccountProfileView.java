package com.floor21.dto;

/** Signed-in account summary for the profile page. */
public record AccountProfileView(
        String displayName,
        String email,
        String roleLabel,
        String projectName,
        boolean builderAdmin,
        boolean platformAdmin,
        boolean vaultPinConfigured,
        boolean expensesPinConfigured,
        boolean vaultAccess,
        String legalCompanyName,
        String panNumber,
        String gstNumber,
        String tanNumber,
        String mobileNumber,
        String address,
        String addressState,
        String addressPin,
        String projectPhone,
        String projectAddress,
        String projectCity,
        boolean companyProfileEditable) {}
