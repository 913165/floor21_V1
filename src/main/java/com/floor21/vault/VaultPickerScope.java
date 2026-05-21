package com.floor21.vault;

/** Special values for the Vault page booking picker (not real booking UUIDs). */
public final class VaultPickerScope {

    /** General vault workspace: income + expenses with no booking. */
    public static final String GENERAL_VAULT = "general-vault";

    /** @deprecated Use {@link #GENERAL_VAULT}; kept for old links and bookmarks. */
    public static final String GENERAL_EXPENSES = "general-expenses";

    private VaultPickerScope() {}

    public static boolean isGeneralVault(String pickerValue) {
        return GENERAL_VAULT.equals(pickerValue) || GENERAL_EXPENSES.equals(pickerValue);
    }
}
