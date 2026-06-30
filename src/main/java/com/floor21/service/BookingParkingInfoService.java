package com.floor21.service;

import com.floor21.dto.LinkedParkingSlotDto;
import com.floor21.entity.Booking;
import com.floor21.entity.Flat;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.util.FlatUnitTypes;
import com.floor21.util.LinkedParkingFormatter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps booking parking info in sync with flat-linked parking slots. */
@Service
@RequiredArgsConstructor
public class BookingParkingInfoService {

    private final BookingRepository bookingRepository;
    private final FlatRepository flatRepository;

    @Transactional(readOnly = true)
    public List<LinkedParkingSlotDto> linkedSlotsForResidentialFlat(UUID residentialFlatId) {
        Flat residential =
                flatRepository
                        .findByIdWithBuilding(residentialFlatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        if (FlatUnitTypes.isParkingCode(residential.getBhkType())
                || Boolean.TRUE.equals(residential.getParking())) {
            return List.of();
        }
        UUID buildingId = residential.getBuilding().getId();
        UUID builderId = residential.getBuilding().getBuilder().getId();
        return flatRepository
                .findLinkedParkingByResidentialFlatId(buildingId, builderId, residentialFlatId)
                .stream()
                .map(
                        f ->
                                new LinkedParkingSlotDto(
                                        f.getId(),
                                        f.getFlatNumber(),
                                        f.getFloorNumber(),
                                        f.getUnitNumber() != null ? f.getUnitNumber() : 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public String parkingDisplayForResidentialFlat(UUID residentialFlatId) {
        return LinkedParkingFormatter.formatSummary(linkedSlotsForResidentialFlat(residentialFlatId));
    }

    @Transactional
    public void syncForResidentialFlat(UUID residentialFlatId) {
        if (residentialFlatId == null) {
            return;
        }
        String summary = parkingDisplayForResidentialFlat(residentialFlatId);
        List<Booking> bookings = bookingRepository.findByFlat_Id(residentialFlatId);
        if (bookings.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Booking booking : bookings) {
            booking.setParkingInfo(summary);
            booking.setUpdatedAt(now);
        }
        bookingRepository.saveAll(bookings);
    }
}
