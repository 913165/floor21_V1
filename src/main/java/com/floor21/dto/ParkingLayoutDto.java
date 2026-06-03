package com.floor21.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ParkingLayoutDto(
        @Min(4) @Max(40) int gridCols,
        @Min(4) @Max(40) int gridRows,
        @NotNull @Valid List<ParkingGridPlacementDto> placements) {}
