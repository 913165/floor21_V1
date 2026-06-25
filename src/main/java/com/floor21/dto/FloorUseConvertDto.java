package com.floor21.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FloorUseConvertDto(
        @NotBlank
        @Pattern(regexp = "PARKING|RESIDENTIAL", message = "target must be PARKING or RESIDENTIAL")
        String target) {}
