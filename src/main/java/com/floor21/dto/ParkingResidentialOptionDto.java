package com.floor21.dto;

import java.util.UUID;

public record ParkingResidentialOptionDto(
        UUID id, String flatNumber, int floorNumber, String bhkType) {}
