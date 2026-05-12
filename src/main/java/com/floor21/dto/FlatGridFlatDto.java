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
        String buyerTooltip,
        /** Active booking client when status is BOOKED; otherwise null. */
        UUID clientId,
        /** Primary line on the card for owner (booked only). */
        String ownerDisplay,
        /** Secondary line: phone, email, or booking code (booked only). */
        String ownerDetail) {}
