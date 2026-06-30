package com.floor21.util;

import com.floor21.dto.LinkedParkingSlotDto;
import java.util.List;
import java.util.stream.Collectors;

public final class LinkedParkingFormatter {

    private LinkedParkingFormatter() {}

    public static String formatSlot(LinkedParkingSlotDto slot) {
        return "Floor "
                + slot.floorNumber()
                + " · Slot "
                + slot.slotNumber()
                + " ("
                + slot.flatNumber()
                + ")";
    }

    public static String formatSummary(List<LinkedParkingSlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        return slots.stream().map(LinkedParkingFormatter::formatSlot).collect(Collectors.joining("; "));
    }
}
