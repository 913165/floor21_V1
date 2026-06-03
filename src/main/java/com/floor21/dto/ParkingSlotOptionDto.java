package com.floor21.dto;

import java.util.UUID;

public record ParkingSlotOptionDto(
        UUID id,
        String flatNumber,
        int floorNumber,
        int slotNumber,
        UUID linkedResidentialFlatId,
        String linkedResidentialFlatNumber) {}
