package com.floor21.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ParkingLayoutDto(
        @Min(1) @Max(40) int gridCols,
        @Min(1) @Max(24) int gridRows,
        @NotNull @Valid List<ParkingGridPlacementDto> placements,
        List<ParkingFixturePlacementDto> fixtures) {}
