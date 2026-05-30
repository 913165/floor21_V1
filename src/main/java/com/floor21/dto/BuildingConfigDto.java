package com.floor21.dto;

import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class BuildingConfigDto {

    @NotNull private Integer totalFloors;
    @NotNull private Integer parkingFloors;
    @NotNull private Integer flatsPerFloor;
    /** Count of each BHK type per residential floor (keys e.g. {@code 1BHK}, {@code 1.5BHK}). */
    private Map<String, Integer> bhkPerFloor = new LinkedHashMap<>();
    /** Legacy fields kept for older forms; generate uses {@link #bhkPerFloor}. */
    @NotNull private Integer bhk1PerFloor;
    @NotNull private Integer bhk2PerFloor;
    @NotNull private Integer bhk3PerFloor;
}
