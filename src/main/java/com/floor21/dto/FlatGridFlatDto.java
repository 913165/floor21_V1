package com.floor21.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FlatGridFlatDto(
        UUID id,
        String flatNumber,
        int floorNumber,
        /** Sequential column position on the floor (1, 2, 3…). */
        Integer unitNumber,
        String bhkType,
        /** Optional column type label (A, B, custom) configured per column. */
        String layoutColumnType,
        BigDecimal basePrice,
        BigDecimal areaSqft,
        BigDecimal carpetAreaSqft,
        BigDecimal balconyAreaSqft,
        String status,
        boolean parking,
        /** Multiline hover text for booked flats (legacy / aria); empty otherwise. */
        String buyerTooltip,
        /** Active booking client when status is BOOKED; otherwise null. */
        UUID clientId,
        /** Primary line on the card for owner (booked only). */
        String ownerDisplay,
        /** Secondary line: phone, email, or booking code (booked only). */
        String ownerDetail,
        String bookingCode,
        /** Active booking id when status is BOOKED; null otherwise. */
        UUID bookingId,
        /** Agreement consideration received (receipts); booked flats only. */
        BigDecimal paymentReceived,
        /** Agreement balance remaining; booked flats only. */
        BigDecimal remainingBalance,
        String buyerPhone,
        String buyerEmail,
        /** Space-separated CSS classes for the card (includes {@code flat-card}). */
        String cardClass,
        /** Assigned sales partner user id; null if none. */
        UUID assignedPartnerId,
        /** Sales partner company (or name) shown on the card; null if none. */
        String assignedPartnerName,
        /** False when another partner owns this flat and the viewer cannot book or hold it. */
        boolean bookableByCurrentUser,
        /** True when this card is the linked upper/other half of a vertical duplex. */
        boolean duplexSecondary,
        /** True when this card is the lower bookable primary of a vertical duplex. */
        boolean duplexPrimary,
        /** Partner flat number for duplex link display (e.g. 1401). */
        String duplexPartnerFlatNumber,
        /** Partner flat id for drawing duplex outline on the grid. */
        UUID duplexPartnerFlatId,
        /** True when this flat absorbed another unit on the same floor. */
        boolean mergePrimary,
        /** True when this card is the linked absorbed half of a same-floor merge. */
        boolean mergeSecondary,
        /** Partner flat id for drawing merge outline on the grid. */
        UUID mergePartnerFlatId,
        /** Absorbed flat id for same-floor merge restore. */
        UUID mergeAbsorbedFlatId,
        /** Absorbed flat number shown in admin restore UI. */
        String mergeAbsorbedFlatNumber,
        /** Label shown on the card type line (may differ from raw bhkType). */
        String gridTypeLabel,
        /** True when this flat has an uploaded layout image. */
        boolean hasLayoutImage) {}
