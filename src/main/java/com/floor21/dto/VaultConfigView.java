package com.floor21.dto;

import java.util.List;
import java.util.UUID;

/** Platform admin: Vault access = user + building combination. */
public record VaultConfigView(
        List<VaultGrantRow> grants,
        List<PickerOption> projects,
        List<PickerOption> users,
        List<PickerOption> buildings) {

    public record VaultGrantRow(
            UUID userId,
            UUID buildingId,
            UUID builderId,
            String userLabel,
            String buildingLabel,
            String builderName,
            boolean enabled) {}

    public record PickerOption(UUID id, String label, UUID builderId) {}
}
