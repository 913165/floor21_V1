package com.floor21.service;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.dto.FlatGridFlatDto;
import com.floor21.dto.FlatGridFloorDto;
import com.floor21.entity.Booking;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlatService {

    private final FlatRepository flatRepository;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<FlatGridFloorDto> getGridData(UUID buildingId) {
        UUID builderId = TenantContext.requireBuilderId();
        List<Flat> flats =
                flatRepository.findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, builderId);
        Map<UUID, Booking> bookingByFlatId = activeBookingsByFlatId(builderId, flats);
        Map<Integer, List<Flat>> byFloor =
                flats.stream().collect(Collectors.groupingBy(Flat::getFloorNumber, TreeMap::new, Collectors.toList()));
        List<Integer> orderedFloors = new ArrayList<>(byFloor.keySet());
        orderedFloors.sort(Comparator.reverseOrder());
        List<FlatGridFloorDto> rows = new ArrayList<>();
        for (Integer floor : orderedFloors) {
            List<FlatGridFlatDto> cells =
                    byFloor.get(floor).stream()
                            .sorted(Comparator.comparing(Flat::getUnitNumber))
                            .map(
                                    f -> {
                                        Booking b = bookingByFlatId.get(f.getId());
                                        return new FlatGridFlatDto(
                                                f.getId(),
                                                f.getFlatNumber(),
                                                f.getFloorNumber(),
                                                f.getBhkType(),
                                                f.getBasePrice(),
                                                f.getAreaSqft(),
                                                f.getStatus(),
                                                Boolean.TRUE.equals(f.getParking()),
                                                buildBuyerTooltip(f, b),
                                                resolveBookedClientId(f, b),
                                                ownerCardTitle(f, b),
                                                ownerCardSubtitle(f, b));
                                    })
                            .toList();
            rows.add(new FlatGridFloorDto("Floor " + floor, cells));
        }
        return rows;
    }

    private Map<UUID, Booking> activeBookingsByFlatId(UUID builderId, List<Flat> flats) {
        List<UUID> flatIds = flats.stream().map(Flat::getId).toList();
        if (flatIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Booking> map = new HashMap<>();
        for (Booking b : bookingRepository.findActiveWithClientByFlatIds(builderId, flatIds)) {
            map.putIfAbsent(b.getFlat().getId(), b);
        }
        return map;
    }

    private static String buildBuyerTooltip(Flat flat, Booking booking) {
        if (!"BOOKED".equals(flat.getStatus()) || booking == null) {
            return "";
        }
        Client c = booking.getClient();
        if (c == null) {
            return "";
        }
        String name = buyerDisplayName(c);
        if (name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Buyer: ").append(name);
        if (booking.getBookingCode() != null && !booking.getBookingCode().isBlank()) {
            sb.append("\nBooking: ").append(booking.getBookingCode());
        }
        String phone = pickPhone(c);
        if (phone != null) {
            sb.append("\nPhone: ").append(phone);
        }
        String email = pickEmail(c);
        if (email != null) {
            sb.append("\nEmail: ").append(email);
        }
        return sb.toString();
    }

    private static String ownerCardTitle(Flat flat, Booking booking) {
        if (!"BOOKED".equals(flat.getStatus()) || booking == null || booking.getClient() == null) {
            return "";
        }
        return buyerDisplayName(booking.getClient());
    }

    private static String ownerCardSubtitle(Flat flat, Booking booking) {
        if (!"BOOKED".equals(flat.getStatus()) || booking == null || booking.getClient() == null) {
            return "";
        }
        Client c = booking.getClient();
        String phone = pickPhone(c);
        if (phone != null) {
            return phone;
        }
        String email = pickEmail(c);
        if (email != null) {
            return email;
        }
        if (booking.getBookingCode() != null && !booking.getBookingCode().isBlank()) {
            return booking.getBookingCode();
        }
        return "";
    }

    private static String buyerDisplayName(Client c) {
        String n = c.displayName();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        if (c.getCompanyName() != null && !c.getCompanyName().isBlank()) {
            return c.getCompanyName().trim();
        }
        return "";
    }

    private static UUID resolveBookedClientId(Flat flat, Booking booking) {
        if (!"BOOKED".equals(flat.getStatus()) || booking == null || booking.getClient() == null) {
            return null;
        }
        return booking.getClient().getId();
    }

    private static String pickEmail(Client c) {
        if (c.getEmail1() != null && !c.getEmail1().isBlank()) {
            return c.getEmail1().trim();
        }
        if (c.getEmail2() != null && !c.getEmail2().isBlank()) {
            return c.getEmail2().trim();
        }
        return null;
    }

    private static String pickPhone(Client c) {
        return Stream.of(c.getMobile1(), c.getMobile2(), c.getPhoneResidence(), c.getPhoneOffice())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void generateFlats(UUID buildingId, BuildingConfigDto cfg) {
        UUID builderId = TenantContext.requireBuilderId();
        Building building =
                buildingRepository
                        .findByIdAndBuilder_Id(buildingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        int total = cfg.getTotalFloors();
        int parking = cfg.getParkingFloors();
        int perFloor = cfg.getFlatsPerFloor();
        int bhk1 = cfg.getBhk1PerFloor();
        int bhk2 = cfg.getBhk2PerFloor();
        int bhk3 = cfg.getBhk3PerFloor();
        if (parking < 0 || parking > total) {
            throw new IllegalArgumentException("Parking floors must be between 0 and total floors");
        }
        int residential = total - parking;
        if (residential > 0 && bhk1 + bhk2 + bhk3 != perFloor) {
            throw new IllegalArgumentException("1BHK + 2BHK + 3BHK must equal flats per floor for residential levels");
        }
        flatRepository.deleteByBuilding_IdAndBuilder_Id(buildingId, builderId);
        flatRepository.flush();

        Builder builder = builderRepository.findById(builderId).orElseThrow();

        building.setTotalFloors(total);
        building.setParkingFloors(parking);
        building.setFlatsPerFloor(perFloor);
        building.setBhk1PerFloor(bhk1);
        building.setBhk2PerFloor(bhk2);
        building.setBhk3PerFloor(bhk3);
        buildingRepository.save(building);

        Instant now = Instant.now();
        List<Flat> batch = new ArrayList<>();
        for (int floor = 1; floor <= parking; floor++) {
            for (int unit = 1; unit <= perFloor; unit++) {
                batch.add(
                        parkingFlat(
                                builder, building, floor, unit, now));
            }
        }
        for (int floor = parking + 1; floor <= total; floor++) {
            int unit = 1;
            for (int i = 0; i < bhk1; i++) {
                batch.add(residentialFlat(builder, building, floor, unit++, "1BHK", 550, 4_500_000, now));
            }
            for (int i = 0; i < bhk2; i++) {
                batch.add(residentialFlat(builder, building, floor, unit++, "2BHK", 850, 7_200_000, now));
            }
            for (int i = 0; i < bhk3; i++) {
                batch.add(residentialFlat(builder, building, floor, unit++, "3BHK", 1100, 9_800_000, now));
            }
        }
        flatRepository.saveAll(batch);
    }

    private Flat parkingFlat(Builder builder, Building building, int floor, int unit, Instant now) {
        Flat f = new Flat();
        f.setBuilder(builder);
        f.setBuilding(building);
        f.setFloorNumber(floor);
        f.setUnitNumber(unit);
        f.setFlatNumber(String.format("%02d%02d", floor, unit));
        f.setBhkType("PKG");
        f.setParking(true);
        f.setStatus("AVAILABLE");
        f.setAreaSqft(BigDecimal.valueOf(150));
        f.setBasePrice(BigDecimal.ZERO);
        f.setCreatedAt(now);
        return f;
    }

    private Flat residentialFlat(
            Builder builder,
            Building building,
            int floor,
            int unit,
            String bhk,
            int area,
            long price,
            Instant now) {
        Flat f = new Flat();
        f.setBuilder(builder);
        f.setBuilding(building);
        f.setFloorNumber(floor);
        f.setUnitNumber(unit);
        f.setFlatNumber(String.format("%02d%02d", floor, unit));
        f.setBhkType(bhk);
        f.setParking(false);
        f.setStatus("AVAILABLE");
        f.setAreaSqft(BigDecimal.valueOf(area));
        f.setBasePrice(BigDecimal.valueOf(price));
        f.setCreatedAt(now);
        return f;
    }

    @Transactional
    public Flat updateStatus(UUID flatId, String status) {
        UUID builderId = TenantContext.requireBuilderId();
        Flat flat =
                flatRepository
                        .findByIdAndBuilder_Id(flatId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        if (Boolean.TRUE.equals(flat.getParking())) {
            throw new IllegalArgumentException("Parking slots cannot change status");
        }
        if (!List.of("AVAILABLE", "HOLD", "BOOKED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
        flat.setStatus(status);
        return flatRepository.save(flat);
    }
}
