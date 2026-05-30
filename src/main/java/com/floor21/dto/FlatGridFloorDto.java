package com.floor21.dto;

import java.util.List;

public record FlatGridFloorDto(int floorNumber, String label, List<FlatGridFlatDto> flats) {}
