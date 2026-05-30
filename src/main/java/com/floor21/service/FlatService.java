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
import com.floor21.util.ResidentialBhkTypes;
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
                                        UUID assignedPartnerId = partnerIds.get(f.getId());
                                        boolean bookable =
                                                partnerFlatAllocationService.isBookableByCurrentUser(
                                                        buildingId, assignedPartnerId);
                                        String cardClass = resolveCardClass(f, b, bookable);
                                        if (!bookable && !Boolean.TRUE.equals(f.getParking())) {
                                            cardClass = cardClass + " flat-card--other-partner";
                                        }
                                        String ownerTitle =
                                                bookable
                                                        ? ownerCardTitle(f, b)
                                                        : ("BOOKED".equals(f.getStatus()) ? "Booked" : "");
                                        String ownerDetail = bookable ? ownerCardSubtitle(f, b) : "";
                                        return new FlatGridFlatDto(
                                                f.getId(),
                                                f.getFlatNumber(),
                                                f.getFloorNumber(),
                                                f.getBhkType(),
                                                f.getBasePrice(),
                                                f.getAreaSqft(),
                                                f.getStatus(),
                                                Boolean.TRUE.equals(f.getParking()),
                                                bookable ? buildBuyerTooltip(f, b) : "",
                                                bookable ? resolveBookedClientId(f, b) : null,
                                                ownerTitle,
                                                ownerDetail,
                                                bookable ? bookingCodeForTooltip(b) : null,
                                                bookable && bookedClient != null ? pickPhone(bookedClient) : null,
                                                bookable && bookedClient != null ? pickEmail(bookedClient) : null,
                                                cardClass,
                                                assignedPartnerId,
                                                partnerLabels.get(f.getId()),
                                                bookable);
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

    private static String resolveCardClass(Flat flat, Booking booking, boolean bookableByCurrentUser) {
        String tone;
        if (Boolean.TRUE.equals(flat.getParking())) {
            tone = "flat-parking";
        } else if ("AVAILABLE".equals(flat.getStatus())) {
            tone = "flat-available";
        } else if ("BOOKED".equals(flat.getStatus())) {
            tone = "flat-booked";
        } else if ("CANCELLED".equals(flat.getStatus())) {
            tone = "flat-deactivated";
        } else {
            tone = "flat-hold";
        }
        String owner = ownerCardTitle(flat, booking);
        boolean hasBuyer =
                bookableByCurrentUser
                        && "BOOKED".equals(flat.getStatus())
                        && owner != null
                        && !owner.isBlank();
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
        Map<String, Integer> mix = resolveBhkMix(cfg);
        if (parking < 0 || parking > total) {
            throw new IllegalArgumentException("Parking floors must be between 0 and total floors");
        }
        int residential = total - parking;
        int mixTotal = ResidentialBhkTypes.sumCounts(mix);
        if (residential > 0 && mixTotal != perFloor) {
            throw new IllegalArgumentException(
                    "BHK counts per floor must add up to flats per floor (currently "
                            + mixTotal
                            + ", expected "
                            + perFloor
                            + ").");
        }
        flatRepository.deleteByBuilding_IdAndBuilder_Id(buildingId, builderId);
        flatRepository.flush();

        Builder builder = builderRepository.findById(builderId).orElseThrow();

        building.setTotalFloors(total);
        building.setParkingFloors(parking);
        building.setFlatsPerFloor(perFloor);
        building.setBhk1PerFloor(mix.getOrDefault("1BHK", 0));
        building.setBhk2PerFloor(mix.getOrDefault("2BHK", 0));
        building.setBhk3PerFloor(mix.getOrDefault("3BHK", 0));
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
            appendResidentialFloorFlats(batch, builder, building, floor, mix, now);
        }
        flatRepository.saveAll(batch);
    }

    @Transactional(readOnly = true)
    public int getTopFloorNumber(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        return flatRepository.findMaxFloorNumberByBuilding_IdAndBuilder_Id(
                buildingId, building.getBuilder().getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> bhkMixForTopResidentialFloor(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        int topFloor = flatRepository.findMaxFloorNumberByBuilding_IdAndBuilder_Id(buildingId, builderId);
        if (topFloor <= 0) {
            return ResidentialBhkTypes.countsFromBuilding(building);
        }
        Map<String, Integer> mix = ResidentialBhkTypes.emptyCountMap();
        for (Flat flat :
                flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                        buildingId, builderId, topFloor)) {
            if (Boolean.TRUE.equals(flat.getParking()) || flat.getBhkType() == null) {
                continue;
            }
            String type = flat.getBhkType().trim().toUpperCase(Locale.ROOT);
            mix.merge(type, 1, Integer::sum);
        }
        if (ResidentialBhkTypes.sumCounts(mix) == 0) {
            return ResidentialBhkTypes.countsFromBuilding(building);
        }
        return mix;
    }

    /**
     * Adds new residential floors above the current top floor without removing existing flats.
     *
     * @return number of floors added
     */
    @Transactional
    public int addFloorsOnTop(UUID buildingId, int additionalFloors, BuildingConfigDto cfg) {
        if (additionalFloors < 1) {
            throw new IllegalArgumentException("Add at least one floor.");
        }
        if (additionalFloors > 50) {
            throw new IllegalArgumentException("Add at most 50 floors at a time.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        long existingFlats = flatRepository.countByBuilding_IdAndBuilder_Id(buildingId, builderId);
        if (existingFlats == 0) {
            throw new IllegalArgumentException(
                    "No flats on the grid yet. Use Generate flats to create the initial layout first.");
        }
        int topFloor = flatRepository.findMaxFloorNumberByBuilding_IdAndBuilder_Id(buildingId, builderId);
        int perFloor =
                cfg.getFlatsPerFloor() != null && cfg.getFlatsPerFloor() > 0
                        ? cfg.getFlatsPerFloor()
                        : building.getFlatsPerFloor();
        if (perFloor == null || perFloor < 1) {
            throw new IllegalArgumentException("Flats per floor must be at least 1.");
        }
        Map<String, Integer> mix = resolveBhkMix(cfg);
        int mixTotal = ResidentialBhkTypes.sumCounts(mix);
        if (mixTotal != perFloor) {
            throw new IllegalArgumentException(
                    "BHK counts per floor must add up to flats per floor (currently "
                            + mixTotal
                            + ", expected "
                            + perFloor
                            + ").");
        }

        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();
        List<Flat> batch = new ArrayList<>();
        int newTop = topFloor + additionalFloors;
        for (int floor = topFloor + 1; floor <= newTop; floor++) {
            appendResidentialFloorFlats(batch, builder, building, floor, mix, now);
        }
        flatRepository.saveAll(batch);

        building.setTotalFloors(
                Math.max(building.getTotalFloors() != null ? building.getTotalFloors() : 0, newTop));
        building.setFlatsPerFloor(perFloor);
        building.setBhk1PerFloor(mix.getOrDefault("1BHK", 0));
        building.setBhk2PerFloor(mix.getOrDefault("2BHK", 0));
        building.setBhk3PerFloor(mix.getOrDefault("3BHK", 0));
        buildingRepository.save(building);
        return additionalFloors;
    }

    private static void appendResidentialFloorFlats(
            List<Flat> batch,
            Builder builder,
            Building building,
            int floor,
            Map<String, Integer> mix,
            Instant now) {
        int unit = 1;
        for (String bhkType : ResidentialBhkTypes.all()) {
            int count = mix.getOrDefault(bhkType, 0);
            for (int i = 0; i < count; i++) {
                batch.add(
                        residentialFlat(
                                builder,
                                building,
                                floor,
                                unit++,
                                bhkType,
                                ResidentialBhkTypes.defaultAreaSqft(bhkType),
                                ResidentialBhkTypes.defaultBasePrice(bhkType),
                                now));
            }
        }
        for (Map.Entry<String, Integer> entry : mix.entrySet()) {
            if (ResidentialBhkTypes.all().contains(entry.getKey())) {
                continue;
            }
            int count = entry.getValue() != null ? entry.getValue() : 0;
            for (int i = 0; i < count; i++) {
                batch.add(
                        residentialFlat(
                                builder,
                                building,
                                floor,
                                unit++,
                                entry.getKey(),
                                ResidentialBhkTypes.defaultAreaSqft(entry.getKey()),
                                ResidentialBhkTypes.defaultBasePrice(entry.getKey()),
                                now));
            }
        }
    }

    private static Map<String, Integer> resolveBhkMix(BuildingConfigDto cfg) {
        Map<String, Integer> mix = ResidentialBhkTypes.emptyCountMap();
        if (cfg.getBhkPerFloor() != null && !cfg.getBhkPerFloor().isEmpty()) {
            for (String type : ResidentialBhkTypes.all()) {
                Integer count = cfg.getBhkPerFloor().get(type);
                mix.put(type, count != null ? Math.max(0, count) : 0);
            }
            return mix;
        }
        mix.put("1BHK", cfg.getBhk1PerFloor() != null ? cfg.getBhk1PerFloor() : 0);
        mix.put("2BHK", cfg.getBhk2PerFloor() != null ? cfg.getBhk2PerFloor() : 0);
        mix.put("3BHK", cfg.getBhk3PerFloor() != null ? cfg.getBhk3PerFloor() : 0);
        return mix;
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
            partnerFlatAllocationService.assertCanManageFlat(flat.getBuilding().getId(), flatId);
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
    public Flat toggleFlatActivationAsPlatformAdmin(UUID flatId) {
        Flat flat = requireResidentialFlatForAdmin(flatId);
        if ("CANCELLED".equals(flat.getStatus())) {
            flat.setStatus("AVAILABLE");
            return flatRepository.save(flat);
        }
        assertNoActiveBooking(flatId, "Cannot deactivate a flat that has an active booking.");
        if ("BOOKED".equals(flat.getStatus())) {
            throw new IllegalArgumentException("Cannot deactivate a booked flat. Cancel the booking first.");
        }
        flat.setStatus("CANCELLED");
        return flatRepository.save(flat);
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
                resolveCardClass(f, booking, true),
                null,
                null,
                true);
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
        return ResidentialBhkTypes.normalize(bhkType);
    }
}
