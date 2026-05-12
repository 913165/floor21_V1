package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FlatGridFlatDto(
        UUID id,
        String flatNumber,
        int floorNumber,
        String bhkType,
        BigDecimal basePrice,
        BigDecimal areaSqft,
        String status,
        boolean parking,
        /** Multiline hover text for booked flats (active booking + client); empty otherwise. */
        String buyerTooltip) {}
