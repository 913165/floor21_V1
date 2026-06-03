package com.floor21.dto;

import java.util.UUID;

public record LinkedParkingSlotDto(
        UUID parkingFlatId, String flatNumber, int floorNumber, int slotNumber) {}
