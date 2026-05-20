package com.floor21.service;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.FlatGridFlatDto;
import com.floor21.dto.FlatGridFloorDto;
import com.floor21.dto.FlatMergeDto;
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
import java.util.Locale;
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

    private static final List<String> RESIDENTIAL_BHK_TYPES =
            List.of("1BHK", "2BHK", "3BHK", "4BHK");

    private final FlatRepository flatRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingService buildingService;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final PartnerFlatAllocationService partnerFlatAllocationService;

    @Transactional(readOnly = true)
    public long countFlatsForBuilding(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        return flatRepository.countByBuilding_IdAndBuilder_Id(buildingId, building.getBuilder().getId());
    }

    @Transactional(readOnly = true)
    public long countActiveBookingsForBuilding(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        return bookingRepository.countActiveByBuilding(building.getBuilder().getId(), buildingId);
    }

    @Transactional(readOnly = true)
    public List<FlatGridFloorDto> getGridData(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        List<Flat> flats =
                flatRepository.findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, builderId);
        Map<UUID, UUID> partnerIds = partnerFlatAllocationService.getFlatOwnerByPartnerId(buildingId);
        Map<UUID, String> partnerLabels = partnerFlatAllocationService.getFlatPartnerLabels(buildingId);
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
                                        Client bookedClient =
                                                b != null && "BOOKED".equals(f.getStatus()) ? b.getClient() : null;
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
                                                ownerCardSubtitle(f, b),
                                                bookingCodeForTooltip(b),
                                                bookedClient != null ? pickPhone(bookedClient) : null,
                                                bookedClient != null ? pickEmail(bookedClient) : null,
                                                resolveCardClass(f, b),
                                                partnerIds.get(f.getId()),
                                                partnerLabels.get(f.getId()));
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

    private static String resolveCardClass(Flat flat, Booking booking) {
        String tone;
        if (Boolean.TRUE.equals(flat.getParking())) {
            tone = "flat-parking";
        } else if ("AVAILABLE".equals(flat.getStatus())) {
            tone = "flat-available";
        } else if ("BOOKED".equals(flat.getStatus())) {
            tone = "flat-booked";
        } else {
            tone = "flat-hold";
        }
        String owner = ownerCardTitle(flat, booking);
        boolean hasBuyer = "BOOKED".equals(flat.getStatus()) && owner != null && !owner.isBlank();
        return hasBuyer ? "flat-card " + tone + " flat-card--has-buyer" : "flat-card " + tone;
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

    private static String bookingCodeForTooltip(Booking booking) {
        if (booking == null || booking.getBookingCode() == null || booking.getBookingCode().isBlank()) {
            return null;
        }
        return booking.getBookingCode().trim();
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
    public void generateFlats(UUID buildingId, BuildingConfigDto cfg, boolean confirmReplace) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        long activeBookings = bookingRepository.countActiveByBuilding(builderId, buildingId);
        if (activeBookings > 0) {
            throw new IllegalArgumentException(
                    "Cannot regenerate flats while "
                            + activeBookings
                            + " active booking(s) exist for this building. Open Bookings to view them. "
                            + "Regenerating would remove flat numbers tied to those deals.");
        }
        long existingFlats = flatRepository.countByBuilding_IdAndBuilder_Id(buildingId, builderId);
        if (existingFlats > 0 && !confirmReplace) {
            throw new IllegalArgumentException(
                    "This building already has "
                            + existingFlats
                            + " flats. Check the confirmation box to replace the entire grid (only when there are no active bookings).");
        }
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
        if (flat.getBuilding() != null) {
            TenantContext.requireBuildingAccess(flat.getBuilding().getId());
        }
        if (Boolean.TRUE.equals(flat.getParking())) {
            throw new IllegalArgumentException("Parking slots cannot change status");
        }
        if (!List.of("AVAILABLE", "HOLD", "BOOKED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
        flat.setStatus(status);
        return flatRepository.save(flat);
    }

    @Transactional
    public Flat updateFlatAsPlatformAdmin(UUID flatId, FlatAdminUpdateDto dto) {
        Flat flat = requireResidentialFlatForAdmin(flatId);
        assertNoActiveBooking(flatId, "Cannot edit flat details while an active booking exists.");
        String bhk = normalizeBhkType(dto.bhkType());
        flat.setBhkType(bhk);
        if (dto.areaSqft() != null) {
            if (dto.areaSqft().signum() <= 0) {
                throw new IllegalArgumentException("Area must be greater than zero.");
            }
            flat.setAreaSqft(dto.areaSqft());
        }
        if (dto.basePrice() != null) {
            if (dto.basePrice().signum() < 0) {
                throw new IllegalArgumentException("Price cannot be negative.");
            }
            flat.setBasePrice(dto.basePrice());
        }
        return flatRepository.save(flat);
    }

    @Transactional
    public void deleteFlatAsPlatformAdmin(UUID flatId) {
        Flat flat = requireResidentialFlatForAdmin(flatId);
        assertNoActiveBooking(flatId, "Cannot remove a flat that has an active booking.");
        if ("BOOKED".equals(flat.getStatus())) {
            throw new IllegalArgumentException("Cannot remove a booked flat. Cancel the booking first.");
        }
        flatRepository.delete(flat);
    }

    @Transactional
    public Flat mergeFlatsAsPlatformAdmin(UUID keepFlatId, FlatMergeDto dto) {
        if (dto.removeFlatId() == null) {
            throw new IllegalArgumentException("Choose which flat to remove.");
        }
        if (keepFlatId.equals(dto.removeFlatId())) {
            throw new IllegalArgumentException("Cannot merge a flat with itself.");
        }
        Flat keep = requireResidentialFlatForAdmin(keepFlatId);
        Flat remove = requireResidentialFlatForAdmin(dto.removeFlatId());
        if (!keep.getBuilding().getId().equals(remove.getBuilding().getId())) {
            throw new IllegalArgumentException("Both flats must belong to the same building.");
        }
        if (!Objects.equals(keep.getFloorNumber(), remove.getFloorNumber())) {
            throw new IllegalArgumentException("Merge is only supported for flats on the same floor.");
        }
        assertNoActiveBooking(keep.getId(), "Cannot merge while the kept flat has an active booking.");
        assertNoActiveBooking(remove.getId(), "Cannot merge while the removed flat has an active booking.");
        if ("BOOKED".equals(remove.getStatus())) {
            throw new IllegalArgumentException("Cannot remove a booked flat. Cancel that booking first.");
        }
        if (!List.of("AVAILABLE", "HOLD").contains(remove.getStatus())) {
            throw new IllegalArgumentException("Only an available or on-hold flat can be removed in a merge.");
        }
        flatRepository.delete(remove);
        String bhk = dto.bhkType() != null && !dto.bhkType().isBlank()
                ? normalizeBhkType(dto.bhkType())
                : keep.getBhkType();
        keep.setBhkType(bhk);
        if (dto.areaSqft() != null) {
            if (dto.areaSqft().signum() <= 0) {
                throw new IllegalArgumentException("Area must be greater than zero.");
            }
            keep.setAreaSqft(dto.areaSqft());
        }
        if (dto.basePrice() != null) {
            if (dto.basePrice().signum() < 0) {
                throw new IllegalArgumentException("Price cannot be negative.");
            }
            keep.setBasePrice(dto.basePrice());
        }
        return flatRepository.save(keep);
    }

    @Transactional(readOnly = true)
    public List<FlatGridFlatDto> listMergeCandidatesOnFloor(UUID keepFlatId) {
        Flat keep = requireResidentialFlatForAdmin(keepFlatId);
        UUID buildingId = keep.getBuilding().getId();
        UUID builderId = keep.getBuilder().getId();
        return flatRepository
                .findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(buildingId, builderId)
                .stream()
                .filter(f -> !Boolean.TRUE.equals(f.getParking()))
                .filter(f -> Objects.equals(f.getFloorNumber(), keep.getFloorNumber()))
                .filter(f -> !f.getId().equals(keep.getId()))
                .filter(f -> List.of("AVAILABLE", "HOLD").contains(f.getStatus()))
                .filter(f -> bookingRepository.countActiveByFlatId(f.getId()) == 0)
                .map(f -> toGridFlatDto(f, null))
                .toList();
    }

    private FlatGridFlatDto toGridFlatDto(Flat f, Booking booking) {
        Client bookedClient =
                booking != null && "BOOKED".equals(f.getStatus()) ? booking.getClient() : null;
        return new FlatGridFlatDto(
                f.getId(),
                f.getFlatNumber(),
                f.getFloorNumber(),
                f.getBhkType(),
                f.getBasePrice(),
                f.getAreaSqft(),
                f.getStatus(),
                Boolean.TRUE.equals(f.getParking()),
                buildBuyerTooltip(f, booking),
                resolveBookedClientId(f, booking),
                ownerCardTitle(f, booking),
                ownerCardSubtitle(f, booking),
                bookingCodeForTooltip(booking),
                bookedClient != null ? pickPhone(bookedClient) : null,
                bookedClient != null ? pickEmail(bookedClient) : null,
                resolveCardClass(f, booking),
                null,
                null);
    }

    private Flat requireResidentialFlatForAdmin(UUID flatId) {
        Flat flat =
                flatRepository
                        .findByIdWithBuilding(flatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        buildingService.resolveForAccess(flat.getBuilding().getId());
        if (Boolean.TRUE.equals(flat.getParking())) {
            throw new IllegalArgumentException("Parking slots cannot be edited.");
        }
        return flat;
    }

    private void assertNoActiveBooking(UUID flatId, String message) {
        if (bookingRepository.countActiveByFlatId(flatId) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalizeBhkType(String bhkType) {
        if (bhkType == null || bhkType.isBlank()) {
            throw new IllegalArgumentException("BHK type is required.");
        }
        String normalized = bhkType.trim().toUpperCase(Locale.ROOT);
        if (!RESIDENTIAL_BHK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("BHK type must be one of: " + String.join(", ", RESIDENTIAL_BHK_TYPES));
        }
        return normalized;
    }
}
