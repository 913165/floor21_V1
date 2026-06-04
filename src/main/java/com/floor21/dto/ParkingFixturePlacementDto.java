package com.floor21.dto;

public record ParkingFixturePlacementDto(
        String kind, int index, int col, int row, String orientation) {}
