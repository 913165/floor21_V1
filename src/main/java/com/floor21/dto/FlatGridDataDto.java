package com.floor21.dto;

import java.util.List;

public record FlatGridDataDto(
        List<FlatGridFloorDto> floors,
        FlatGridGroundFloorDto groundFloor,
        List<FlatGridBasementDto> basements) {}

