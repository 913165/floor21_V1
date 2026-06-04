package com.floor21.dto;

import jakarta.validation.constraints.NotNull;

public record ParkingGridColDto(@NotNull Action action) {

    public enum Action {
        INSERT_LEFT,
        INSERT_RIGHT,
        REMOVE_LEFT,
        REMOVE_RIGHT
    }
}
