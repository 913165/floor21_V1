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
    /** Optional left-to-right column order; length must equal {@link #flatsPerFloor} and match {@link #bhkPerFloor} counts. */
    private java.util.List<String> columnBhkOrder = new java.util.ArrayList<>();
    /** Legacy fields kept for older forms; generate uses {@link #bhkPerFloor}. */
    @NotNull private Integer bhk1PerFloor;
    @NotNull private Integer bhk2PerFloor;
    @NotNull private Integer bhk3PerFloor;
    /** Comma-separated floor numbers to skip (e.g. {@code 13} or {@code 4,13}). */
    private String skippedFloorNumbers;
}
