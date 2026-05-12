package com.floor21.dto;

import java.util.UUID;

/** Building + one flat id for linking from client detail to the flat chart. */
public record ClientBuildingNavDto(UUID buildingId, String buildingName, UUID focusFlatId) {}
