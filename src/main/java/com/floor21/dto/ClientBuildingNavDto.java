package com.floor21.dto;

import java.util.UUID;

/** Building + flat/booking ids for linking from client detail to flats, receipts, and schedule. */
public record ClientBuildingNavDto(
        UUID buildingId, String buildingName, UUID focusFlatId, UUID bookingId) {}
