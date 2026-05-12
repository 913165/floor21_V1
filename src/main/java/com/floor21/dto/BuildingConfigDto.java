package com.floor21.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class BuildingConfigDto {

    @NotNull private Integer totalFloors;
    @NotNull private Integer parkingFloors;
    @NotNull private Integer flatsPerFloor;
    @NotNull private Integer bhk1PerFloor;
    @NotNull private Integer bhk2PerFloor;
    @NotNull private Integer bhk3PerFloor;
}
