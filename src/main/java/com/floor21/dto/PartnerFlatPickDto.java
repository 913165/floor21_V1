package com.floor21.dto;

import java.util.UUID;

public record PartnerFlatPickDto(UUID id, String flatNumber, int floorNumber, String bhkType) {}
