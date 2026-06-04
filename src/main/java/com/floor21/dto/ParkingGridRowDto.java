package com.floor21.dto;

import jakarta.validation.constraints.NotNull;

public record ParkingGridRowDto(@NotNull Action action) {

    public enum Action {
        INSERT_TOP,
        INSERT_BOTTOM,
        REMOVE_TOP,
        REMOVE_BOTTOM
    }
}
