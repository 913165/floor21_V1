package com.floor21.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GroundFloorLayoutDto(
        @NotNull Integer gridCols,
        @NotNull Integer gridRows,
        @NotNull List<ParkingGridPlacementDto> shopPlacements,
        List<ParkingGridPlacementDto> parkingPlacements,
        List<ParkingFixturePlacementDto> fixtures) {}
