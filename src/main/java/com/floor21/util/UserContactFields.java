package com.floor21.util;

import com.floor21.entity.User;

public final class UserContactFields {

    private UserContactFields() {}

    public static void applyFromForm(User entity, User form) {
        entity.setCompanyName(requireField(form.getCompanyName(), "Company name is required."));

        String normalizedPan = IndianTaxIds.normalizePan(requireField(form.getPanNumber(), "PAN is required."));
        if (!IndianTaxIds.isValidPan(normalizedPan)) {
            throw new IllegalArgumentException("PAN must be 10 characters (e.g. ABCDE1234F).");
        }
        entity.setPanNumber(normalizedPan);

        String normalizedTan = IndianTaxIds.normalizeTan(form.getTanNumber());
        if (normalizedTan != null && !IndianTaxIds.isValidTan(normalizedTan)) {
            throw new IllegalArgumentException("TAN must be 10 characters (e.g. DELH12345A).");
        }
        entity.setTanNumber(normalizedTan);

        String normalizedGst = IndianTaxIds.normalizeGstin(requireField(form.getGstNumber(), "GST number is required."));
        if (!IndianTaxIds.isValidGstin(normalizedGst)) {
            throw new IllegalArgumentException("GST number must be a valid 15-character GSTIN.");
        }
        entity.setGstNumber(normalizedGst);

        String mobile = requireField(form.getMobileNumber(), "Mobile number is required.");
        String normalizedMobile = IndianTaxIds.normalizeMobile(mobile);
        if (!IndianTaxIds.isValidMobile(normalizedMobile)) {
            throw new IllegalArgumentException("Mobile number must be 10 digits starting with 6–9.");
        }
        entity.setMobileNumber(normalizedMobile);

        entity.setAddress(requireField(form.getAddress(), "Address is required."));

        String state = IndianStates.normalizeState(form.getAddressState());
        if (state == null) {
            throw new IllegalArgumentException("State is required.");
        }
        if (!IndianStates.isKnownState(state)) {
            throw new IllegalArgumentException("Please select a valid state or union territory.");
        }
        entity.setAddressState(state);

        String pin = IndianStates.normalizePin(form.getAddressPin());
        if (pin == null) {
            throw new IllegalArgumentException("PIN code is required.");
        }
        if (!IndianStates.isValidPin(pin)) {
            throw new IllegalArgumentException(
                    "PIN code must be a valid 6-digit Indian postal code (e.g. 110001).");
        }
        entity.setAddressPin(pin);
    }

    private static String requireField(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
