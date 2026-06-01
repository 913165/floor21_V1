package com.floor21.service;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.FlatGridFlatDto;
import com.floor21.dto.FlatGridFloorDto;
import com.floor21.dto.FlatMergeCandidateDto;
import com.floor21.dto.FlatMergeDto;
import com.floor21.dto.FloorMergeSplitResult;
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
import com.floor21.util.FlatUnitTypes;
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
        List<Flat> allFlats =
                flatRepository.findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, builderId);
        List<Flat> flats = allFlats;
        Map<UUID, UUID> partnerIds = partnerFlatAllocationService.getFlatOwnerByPartnerId(buildingId);
        Map<UUID, String> partnerLabels = partnerFlatAllocationService.getFlatPartnerLabels(buildingId);
        Map<UUID, Booking> bookingByFlatId = activeBookingsByFlatId(builderId, allFlats);
        Map<UUID, Flat> flatById =
                allFlats.stream().collect(Collectors.toMap(Flat::getId, f -> f, (a, b) -> a, HashMap::new));
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
                                    f ->
                                            toGridFlatDto(
                                                    f,
                                                    bookingByFlatId,
                                                    flatById,
                                                    buildingId,
                                                    partnerIds,
                                                    partnerLabels))
                            .toList();
            rows.add(new FlatGridFloorDto(floor, "Floor " + floor, cells));
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

    private static String resolveCardClass(
            Flat flat, Booking booking, boolean bookableByCurrentUser, Map<UUID, Flat> flatById) {
        String tone;
        if (FlatUnitTypes.isDuplexSecondary(flat)) {
            tone = "flat-duplex-part";
        } else if (FlatUnitTypes.isMergeAbsorbed(flat)) {
            tone = "flat-merge-part";
        } else if (FlatUnitTypes.isNonBookable(flat)) {
            tone = FlatUnitTypes.isParkingCode(flat.getBhkType()) || Boolean.TRUE.equals(flat.getParking())
                    ? "flat-parking"
                    : "flat-amenity";
        } else if (FlatUnitTypes.isDuplexPrimary(flat)) {
            if ("BOOKED".equals(flat.getStatus())) {
                tone = "flat-booked";
            } else if ("CANCELLED".equals(flat.getStatus())) {
                tone = "flat-deactivated";
            } else if ("HOLD".equals(flat.getStatus())) {
                tone = "flat-hold";
            } else {
                tone = "flat-duplex-primary";
            }
        } else if (FlatUnitTypes.isMergePrimary(flat)) {
            if ("BOOKED".equals(flat.getStatus())) {
                tone = "flat-booked";
            } else if ("CANCELLED".equals(flat.getStatus())) {
                tone = "flat-deactivated";
            } else if ("HOLD".equals(flat.getStatus())) {
                tone = "flat-hold";
            } else {
                tone = "flat-merge-primary";
            }
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
                        && bookingShowsOnCard(flat, flatById, booking)
                        && owner != null
                        && !owner.isBlank();
        String linkClass = "";
        if (FlatUnitTypes.isDuplexPrimary(flat)) {
            linkClass = " flat-duplex";
        } else if (FlatUnitTypes.isMergePrimary(flat)) {
            linkClass = " flat-merge";
        }
        return hasBuyer
                ? "flat-card " + tone + linkClass + " flat-card--has-buyer"
                : "flat-card " + tone + linkClass;
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

    /**
     * Rebuilds the flat grid when floor layout settings changed and the building has no bookings.
     * No-op when layout fields are unchanged or there are no flats yet (use generate on the flat grid instead).
     */
    @Transactional
    public void regenerateLayoutIfChanged(Building before, Building saved) {
        if (layoutConfigEquals(before, saved)) {
            return;
        }
        long flatCount = countFlatsForBuilding(saved.getId());
        if (flatCount == 0) {
            return;
        }
        generateFlats(saved.getId(), configFromBuilding(saved), true);
    }

    private static BuildingConfigDto configFromBuilding(Building building) {
        BuildingConfigDto cfg = new BuildingConfigDto();
        cfg.setTotalFloors(building.getTotalFloors());
        cfg.setParkingFloors(building.getParkingFloors() != null ? building.getParkingFloors() : 0);
        cfg.setFlatsPerFloor(building.getFlatsPerFloor());
        Map<String, Integer> mix = ResidentialBhkTypes.countsFromBuilding(building);
        cfg.setBhkPerFloor(mix);
        cfg.setBhk1PerFloor(mix.getOrDefault("1BHK", 0));
        cfg.setBhk2PerFloor(mix.getOrDefault("2BHK", 0));
        cfg.setBhk3PerFloor(mix.getOrDefault("3BHK", 0));
        return cfg;
    }

    private static boolean layoutConfigEquals(Building a, Building b) {
        return Objects.equals(a.getTotalFloors(), b.getTotalFloors())
                && Objects.equals(
                        a.getParkingFloors() != null ? a.getParkingFloors() : 0,
                        b.getParkingFloors() != null ? b.getParkingFloors() : 0)
                && Objects.equals(a.getFlatsPerFloor(), b.getFlatsPerFloor())
                && ResidentialBhkTypes.countsFromBuilding(a).equals(ResidentialBhkTypes.countsFromBuilding(b));
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
        ResidentialBhkTypes.persistMixOnBuilding(building, mix);
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
        Integer perFloorSetting =
                cfg.getFlatsPerFloor() != null && cfg.getFlatsPerFloor() > 0
                        ? cfg.getFlatsPerFloor()
                        : building.getFlatsPerFloor();
        if (perFloorSetting == null || perFloorSetting < 1) {
            throw new IllegalArgumentException("Flats per floor must be at least 1.");
        }
        int perFloor = perFloorSetting;
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
        ResidentialBhkTypes.persistMixOnBuilding(building, mix);
        buildingRepository.save(building);
        return additionalFloors;
    }

    @Transactional(readOnly = true)
    public int getMaxRemovableTopFloors(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        int topFloor =
                flatRepository.findMaxFloorNumberByBuilding_IdAndBuilder_Id(
                        buildingId, building.getBuilder().getId());
        int parking = building.getParkingFloors() != null ? building.getParkingFloors() : 0;
        return Math.max(0, topFloor - parking - 1);
    }

    /**
     * Removes the top residential floor(s) and their flats. Parking floors cannot be removed.
     *
     * @return number of floors removed
     */
    @Transactional
    public int removeTopFloors(UUID buildingId, int floorsToRemove) {
        if (floorsToRemove < 1) {
            throw new IllegalArgumentException("Remove at least one floor.");
        }
        if (floorsToRemove > 50) {
            throw new IllegalArgumentException("Remove at most 50 floors at a time.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        int topFloor = flatRepository.findMaxFloorNumberByBuilding_IdAndBuilder_Id(buildingId, builderId);
        if (topFloor <= 0) {
            throw new IllegalArgumentException("No flats on the grid yet.");
        }
        int parking = building.getParkingFloors() != null ? building.getParkingFloors() : 0;
        int minResidentialTop = parking + 1;
        if (topFloor <= parking) {
            throw new IllegalArgumentException("Only residential floors above parking can be removed.");
        }
        int maxRemovable = topFloor - minResidentialTop;
        if (floorsToRemove > maxRemovable) {
            throw new IllegalArgumentException(
                    "Cannot remove "
                            + floorsToRemove
                            + " floor(s). At most "
                            + maxRemovable
                            + " can be removed while keeping floor "
                            + minResidentialTop
                            + ".");
        }
        int fromFloor = topFloor - floorsToRemove + 1;
        List<Flat> flatsToRemove =
                flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberBetweenOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, builderId, fromFloor, topFloor);
        if (flatsToRemove.isEmpty()) {
            throw new IllegalArgumentException("No flats found on the top floor(s) to remove.");
        }
        for (Flat flat : flatsToRemove) {
            validateFlatRemovableForTopFloorDeletion(flat);
        }
        flatRepository.deleteByBuilding_IdAndBuilder_IdAndFloorNumberBetween(
                buildingId, builderId, fromFloor, topFloor);
        int newTop = topFloor - floorsToRemove;
        building.setTotalFloors(Math.max(parking, newTop));
        buildingRepository.save(building);
        return floorsToRemove;
    }

    private void validateFlatRemovableForTopFloorDeletion(Flat flat) {
        if (FlatUnitTypes.isMergePrimary(flat) || FlatUnitTypes.isMergeAbsorbed(flat)) {
            throw new IllegalArgumentException(
                    "Floor "
                            + flat.getFloorNumber()
                            + " has merged units. Restore them before removing the floor.");
        }
        if (FlatUnitTypes.isDuplexPrimary(flat) || FlatUnitTypes.isDuplexSecondary(flat)) {
            throw new IllegalArgumentException(
                    "Floor "
                            + flat.getFloorNumber()
                            + " has duplex units. Split the duplex before removing the floor.");
        }
        assertNoActiveBooking(
                flat.getId(),
                "Cannot remove floor "
                        + flat.getFloorNumber()
                        + " while flat "
                        + flat.getFlatNumber()
                        + " has an active booking.");
        if ("BOOKED".equals(flat.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot remove floor "
                            + flat.getFloorNumber()
                            + " while flat "
                            + flat.getFlatNumber()
                            + " is booked. Cancel the booking first.");
        }
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

    private static Flat parkingFlat(Builder builder, Building building, int floor, int unit, Instant now) {
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

    private static Flat residentialFlat(
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
        if (FlatUnitTypes.isNonBookable(flat)) {
            throw new IllegalArgumentException("Parking and amenity units cannot change status");
        }
        if (!List.of("AVAILABLE", "HOLD", "BOOKED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }
        flat.setStatus(status);
        return flatRepository.save(flat);
    }

    @Transactional
    public Flat updateFlatAsPlatformAdmin(UUID flatId, FlatAdminUpdateDto dto) {
        Flat flat = requireFlatForAdmin(flatId);
        if (FlatUnitTypes.isDuplexSecondary(flat)) {
            throw new IllegalArgumentException("Split the duplex before editing the linked upper unit.");
        }
        if (FlatUnitTypes.isMergeAbsorbed(flat)) {
            throw new IllegalArgumentException("Restore the floor merge before editing the linked unit.");
        }
        assertNoActiveBooking(flatId, "Cannot edit flat details while an active booking exists.");
        FlatUnitTypes.applyToFlat(flat, dto.bhkType(), dto.areaSqft(), dto.basePrice());
        return flatRepository.save(flat);
    }

    @Transactional
    public List<Flat> updateFloorAsPlatformAdmin(UUID buildingId, int floorNumber, FlatAdminUpdateDto dto) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        List<Flat> flats =
                flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                        buildingId, builderId, floorNumber);
        if (flats.isEmpty()) {
            throw new IllegalArgumentException("No units on floor " + floorNumber + ".");
        }
        if (flats.stream().anyMatch(FlatUnitTypes::isDuplexSecondary)) {
            throw new IllegalArgumentException("Split duplex unit(s) on this floor before changing the whole floor.");
        }
        if (flats.stream().anyMatch(FlatUnitTypes::isDuplexPrimary)) {
            throw new IllegalArgumentException("Split duplex unit(s) on this floor before changing the whole floor.");
        }
        if (flats.stream().anyMatch(FlatUnitTypes::isMergePrimary)) {
            throw new IllegalArgumentException("Restore merged unit(s) on this floor before changing the whole floor.");
        }
        long blocked =
                flats.stream()
                        .filter(f -> !FlatUnitTypes.isMergeAbsorbed(f))
                        .filter(f -> bookingRepository.countActiveByFlatId(f.getId()) > 0)
                        .count();
        if (blocked > 0) {
            throw new IllegalArgumentException(
                    "Cannot change floor "
                            + floorNumber
                            + " while "
                            + blocked
                            + " unit(s) have an active booking.");
        }
        for (Flat flat : flats) {
            if (FlatUnitTypes.isMergeAbsorbed(flat)) {
                continue;
            }
            FlatUnitTypes.applyToFlat(flat, dto.bhkType(), dto.areaSqft(), dto.basePrice());
        }
        return flatRepository.saveAll(flats);
    }

    @Transactional
    public void deleteFlatAsPlatformAdmin(UUID flatId) {
        Flat flat = requireResidentialFlatForAdmin(flatId);
        if (FlatUnitTypes.isMergePrimary(flat)) {
            throw new IllegalArgumentException("Restore the merged unit before removing this flat.");
        }
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
        assertNotInDuplex(keep);
        assertNotInDuplex(remove);
        assertNotMerged(keep);
        assertNotMerged(remove);
        assertNoActiveBooking(keep.getId(), "Cannot merge while the kept flat has an active booking.");
        assertNoActiveBooking(remove.getId(), "Cannot merge while the removed flat has an active booking.");
        if ("BOOKED".equals(remove.getStatus()) || "BOOKED".equals(keep.getStatus())) {
            throw new IllegalArgumentException("Cannot merge a booked flat. Cancel the booking first.");
        }
        if (!List.of("AVAILABLE", "HOLD").contains(remove.getStatus())
                || !List.of("AVAILABLE", "HOLD").contains(keep.getStatus())) {
            throw new IllegalArgumentException("Only available or on-hold flats can be merged.");
        }

        if (!Objects.equals(keep.getFloorNumber(), remove.getFloorNumber())) {
            return mergeVerticalDuplex(keep, remove, dto);
        }

        return mergeSameFloor(keep, remove, dto);
    }

    @Transactional
    public FloorMergeSplitResult splitMergedFlatAsPlatformAdmin(UUID flatId) {
        Flat flat = requireFlatForAdmin(flatId);
        Flat keep =
                FlatUnitTypes.isMergePrimary(flat)
                        ? flat
                        : flatRepository
                                .findById(flat.getMergedIntoFlatId())
                                .orElseThrow(() -> new ResourceNotFoundException("Merge primary flat not found"));
        if (!FlatUnitTypes.isMergePrimary(keep)) {
            throw new IllegalArgumentException("This flat has no merged unit to restore.");
        }
        Flat absorbed =
                flatRepository
                        .findById(keep.getMergedAbsorbedFlatId())
                        .orElseThrow(() -> new ResourceNotFoundException("Merged flat not found"));
        if (!FlatUnitTypes.isMergeAbsorbed(absorbed)) {
            throw new IllegalArgumentException("Merged unit link is invalid. Contact support.");
        }
        assertNoActiveBooking(keep.getId(), "Cannot restore merged unit while this flat has an active booking.");
        if (keep.getPreMergeBhkType() != null) {
            keep.setBhkType(keep.getPreMergeBhkType());
        }
        if (keep.getPreMergeAreaSqft() != null) {
            keep.setAreaSqft(keep.getPreMergeAreaSqft());
        }
        if (keep.getPreMergeBasePrice() != null) {
            keep.setBasePrice(keep.getPreMergeBasePrice());
        }
        if (keep.getPreMergeStatus() != null && !"BOOKED".equals(keep.getStatus())) {
            keep.setStatus(keep.getPreMergeStatus());
        }
        keep.setMergedAbsorbedFlatId(null);
        keep.setPreMergeBhkType(null);
        keep.setPreMergeAreaSqft(null);
        keep.setPreMergeBasePrice(null);
        keep.setPreMergeStatus(null);
        absorbed.setMergedIntoFlatId(null);
        absorbed.setStatus(
                absorbed.getPreMergeStatus() != null && !absorbed.getPreMergeStatus().isBlank()
                        ? absorbed.getPreMergeStatus()
                        : "AVAILABLE");
        absorbed.setPreMergeStatus(null);
        flatRepository.save(absorbed);
        return new FloorMergeSplitResult(flatRepository.save(keep), absorbed);
    }

    private Flat mergeSameFloor(Flat keep, Flat remove, FlatMergeDto dto) {
        keep.setPreMergeBhkType(keep.getBhkType());
        keep.setPreMergeAreaSqft(keep.getAreaSqft());
        keep.setPreMergeBasePrice(keep.getBasePrice());
        keep.setPreMergeStatus(keep.getStatus());
        remove.setPreMergeStatus(remove.getStatus());
        keep.setMergedAbsorbedFlatId(remove.getId());
        remove.setMergedIntoFlatId(keep.getId());
        applyMergedDetails(keep, dto, false, null, null);
        flatRepository.save(remove);
        return flatRepository.save(keep);
    }

    @Transactional
    public Flat splitDuplexAsPlatformAdmin(UUID flatId) {
        Flat flat = requireFlatForAdmin(flatId);
        if (!FlatUnitTypes.isDuplexPrimary(flat) && !FlatUnitTypes.isDuplexSecondary(flat)) {
            throw new IllegalArgumentException("This flat is not part of a duplex.");
        }
        Flat primary =
                FlatUnitTypes.isDuplexPrimary(flat)
                        ? flat
                        : flatRepository
                                .findById(flat.getDuplexPrimaryFlatId())
                                .orElseThrow(() -> new ResourceNotFoundException("Duplex primary flat not found"));
        Flat secondary =
                flatRepository
                        .findById(primary.getDuplexSecondaryFlatId())
                        .orElseThrow(() -> new ResourceNotFoundException("Duplex linked flat not found"));
        assertNoActiveBooking(primary.getId(), "Cannot split duplex while the primary flat has an active booking.");
        primary.setDuplexSecondaryFlatId(null);
        secondary.setDuplexPrimaryFlatId(null);
        if ("DUPLEX".equals(primary.getBhkType()) && secondary.getBhkType() != null && !secondary.getBhkType().isBlank()) {
            primary.setBhkType(secondary.getBhkType());
        }
        if (secondary.getAreaSqft() != null) {
            primary.setAreaSqft(secondary.getAreaSqft());
        }
        if (secondary.getBasePrice() != null) {
            primary.setBasePrice(secondary.getBasePrice());
        }
        if (!"BOOKED".equals(secondary.getStatus())) {
            secondary.setStatus("AVAILABLE");
        }
        flatRepository.save(secondary);
        return flatRepository.save(primary);
    }

    private Flat mergeVerticalDuplex(Flat keep, Flat remove, FlatMergeDto dto) {
        int floorDiff = Math.abs(keep.getFloorNumber() - remove.getFloorNumber());
        if (floorDiff != 1) {
            throw new IllegalArgumentException(
                    "Duplex merge requires adjacent floors (e.g. floor 13 and 14).");
        }
        if (!Objects.equals(keep.getUnitNumber(), remove.getUnitNumber())) {
            throw new IllegalArgumentException(
                    "Duplex merge requires the same unit stack (e.g. 1301 with 1401).");
        }
        Flat lower = keep.getFloorNumber() < remove.getFloorNumber() ? keep : remove;
        Flat upper = keep.getFloorNumber() < remove.getFloorNumber() ? remove : keep;
        BigDecimal defaultArea = sumNullable(lower.getAreaSqft(), upper.getAreaSqft());
        BigDecimal defaultPrice = sumNullable(lower.getBasePrice(), upper.getBasePrice());
        applyMergedDetails(lower, dto, true, defaultArea, defaultPrice);
        lower.setDuplexSecondaryFlatId(upper.getId());
        upper.setDuplexPrimaryFlatId(lower.getId());
        upper.setStatus("AVAILABLE");
        flatRepository.save(upper);
        return flatRepository.save(lower);
    }

    private void applyMergedDetails(
            Flat target,
            FlatMergeDto dto,
            boolean verticalDuplex,
            BigDecimal defaultArea,
            BigDecimal defaultPrice) {
        String bhk =
                dto.bhkType() != null && !dto.bhkType().isBlank()
                        ? normalizeBhkType(dto.bhkType())
                        : (verticalDuplex ? "DUPLEX" : target.getBhkType());
        target.setBhkType(bhk);
        BigDecimal area = dto.areaSqft() != null ? dto.areaSqft() : defaultArea;
        if (area != null) {
            if (area.signum() <= 0) {
                throw new IllegalArgumentException("Area must be greater than zero.");
            }
            target.setAreaSqft(area);
        }
        BigDecimal price = dto.basePrice() != null ? dto.basePrice() : defaultPrice;
        if (price != null) {
            if (price.signum() < 0) {
                throw new IllegalArgumentException("Price cannot be negative.");
            }
            target.setBasePrice(price);
        }
    }

    private static BigDecimal sumNullable(BigDecimal left, BigDecimal right) {
        BigDecimal a = left != null ? left : BigDecimal.ZERO;
        BigDecimal b = right != null ? right : BigDecimal.ZERO;
        return a.add(b);
    }

    private static void assertNotInDuplex(Flat flat) {
        if (FlatUnitTypes.isDuplexPrimary(flat) || FlatUnitTypes.isDuplexSecondary(flat)) {
            throw new IllegalArgumentException(
                    "Flat " + flat.getFlatNumber() + " is already part of a duplex. Split it first.");
        }
    }

    private static void assertNotMerged(Flat flat) {
        if (FlatUnitTypes.isMergePrimary(flat) || FlatUnitTypes.isMergeAbsorbed(flat)) {
            throw new IllegalArgumentException(
                    "Flat "
                            + flat.getFlatNumber()
                            + " is already part of a floor merge. Restore it first.");
        }
    }

    @Transactional(readOnly = true)
    public List<FlatMergeCandidateDto> listMergeCandidates(UUID keepFlatId) {
        Flat keep = requireResidentialFlatForAdmin(keepFlatId);
        UUID buildingId = keep.getBuilding().getId();
        UUID builderId = keep.getBuilder().getId();
        return flatRepository
                .findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(buildingId, builderId)
                .stream()
                .filter(f -> !FlatUnitTypes.isNonBookable(f))
                .filter(f -> !FlatUnitTypes.isDuplexPrimary(f))
                .filter(f -> !FlatUnitTypes.isDuplexSecondary(f))
                .filter(f -> !FlatUnitTypes.isMergeAbsorbed(f))
                .filter(f -> !FlatUnitTypes.isMergePrimary(f))
                .filter(f -> !f.getId().equals(keep.getId()))
                .filter(f -> List.of("AVAILABLE", "HOLD").contains(f.getStatus()))
                .filter(f -> bookingRepository.countActiveByFlatId(f.getId()) == 0)
                .filter(
                        f ->
                                Objects.equals(f.getFloorNumber(), keep.getFloorNumber())
                                        || isVerticalDuplexCandidate(keep, f))
                .map(
                        f ->
                                new FlatMergeCandidateDto(
                                        f.getId(),
                                        f.getFlatNumber(),
                                        f.getFloorNumber(),
                                        f.getBhkType(),
                                        f.getStatus(),
                                        !Objects.equals(f.getFloorNumber(), keep.getFloorNumber())))
                .toList();
    }

    private static boolean isVerticalDuplexCandidate(Flat keep, Flat candidate) {
        if (!Objects.equals(keep.getUnitNumber(), candidate.getUnitNumber())) {
            return false;
        }
        return Math.abs(keep.getFloorNumber() - candidate.getFloorNumber()) == 1;
    }

    private FlatGridFlatDto toGridFlatDto(
            Flat f,
            Map<UUID, Booking> bookingByFlatId,
            Map<UUID, Flat> flatById,
            UUID buildingId,
            Map<UUID, UUID> partnerIds,
            Map<UUID, String> partnerLabels) {
        Booking b = resolveBookingForGridCard(f, bookingByFlatId, flatById);
        Client bookedClient = b != null && bookingShowsOnCard(f, flatById, b) ? b.getClient() : null;
        UUID assignedPartnerId = partnerIds.get(f.getId());
        boolean bookable =
                !FlatUnitTypes.isDuplexSecondary(f)
                        && !FlatUnitTypes.isMergeAbsorbed(f)
                        && partnerFlatAllocationService.isBookableByCurrentUser(
                                buildingId, assignedPartnerId);
        String cardClass = resolveCardClass(f, b, bookable, flatById);
        if (!bookable && !FlatUnitTypes.isNonBookable(f)) {
            cardClass = cardClass + " flat-card--other-partner";
        }
        String ownerTitle = resolveGridOwnerTitle(f, b, bookable, flatById);
        String ownerDetail = bookable ? ownerCardSubtitle(f, b) : resolveLinkedUnitDetail(f, flatById, b);
        String partnerFlatNumber = duplexPartnerFlatNumber(f, flatById);
        UUID partnerFlatId = duplexPartnerFlatId(f);
        Flat mergeAbsorbed = mergeAbsorbedFlat(f, flatById);
        return new FlatGridFlatDto(
                f.getId(),
                f.getFlatNumber(),
                f.getFloorNumber(),
                f.getBhkType(),
                f.getBasePrice(),
                f.getAreaSqft(),
                f.getStatus(),
                Boolean.TRUE.equals(f.getParking()),
                bookable ? buildBuyerTooltip(f, b) : buildLinkedUnitTooltip(f, flatById, b),
                bookable ? resolveBookedClientId(f, b) : null,
                ownerTitle,
                ownerDetail,
                bookable ? bookingCodeForTooltip(b) : null,
                bookedClient != null ? pickPhone(bookedClient) : null,
                bookedClient != null ? pickEmail(bookedClient) : null,
                cardClass,
                assignedPartnerId,
                partnerLabels.get(f.getId()),
                bookable,
                FlatUnitTypes.isDuplexSecondary(f),
                FlatUnitTypes.isDuplexPrimary(f),
                partnerFlatNumber,
                partnerFlatId,
                FlatUnitTypes.isMergePrimary(f),
                FlatUnitTypes.isMergeAbsorbed(f),
                mergePartnerFlatId(f),
                mergeAbsorbed != null ? mergeAbsorbed.getId() : null,
                mergeAbsorbed != null ? mergeAbsorbed.getFlatNumber() : null,
                gridTypeLabel(f, flatById));
    }

    private static Flat mergeAbsorbedFlat(Flat flat, Map<UUID, Flat> flatById) {
        if (!FlatUnitTypes.isMergePrimary(flat) || flat.getMergedAbsorbedFlatId() == null) {
            return null;
        }
        return flatById.get(flat.getMergedAbsorbedFlatId());
    }

    private static UUID mergePartnerFlatId(Flat flat) {
        if (FlatUnitTypes.isMergePrimary(flat)) {
            return flat.getMergedAbsorbedFlatId();
        }
        if (FlatUnitTypes.isMergeAbsorbed(flat)) {
            return flat.getMergedIntoFlatId();
        }
        return null;
    }

    private static UUID duplexPartnerFlatId(Flat flat) {
        if (FlatUnitTypes.isDuplexPrimary(flat)) {
            return flat.getDuplexSecondaryFlatId();
        }
        if (FlatUnitTypes.isDuplexSecondary(flat)) {
            return flat.getDuplexPrimaryFlatId();
        }
        return null;
    }

    private static Booking resolveBookingForGridCard(
            Flat f, Map<UUID, Booking> bookingByFlatId, Map<UUID, Flat> flatById) {
        Booking direct = bookingByFlatId.get(f.getId());
        if (direct != null) {
            return direct;
        }
        if (FlatUnitTypes.isDuplexSecondary(f) && f.getDuplexPrimaryFlatId() != null) {
            return bookingByFlatId.get(f.getDuplexPrimaryFlatId());
        }
        if (FlatUnitTypes.isMergeAbsorbed(f) && f.getMergedIntoFlatId() != null) {
            return bookingByFlatId.get(f.getMergedIntoFlatId());
        }
        return null;
    }

    private static boolean bookingShowsOnCard(Flat f, Map<UUID, Flat> flatById, Booking b) {
        if (b == null) {
            return false;
        }
        if (FlatUnitTypes.isDuplexSecondary(f)) {
            Flat primary = flatById.get(f.getDuplexPrimaryFlatId());
            return primary != null && "BOOKED".equals(primary.getStatus());
        }
        if (FlatUnitTypes.isMergeAbsorbed(f)) {
            Flat keep = flatById.get(f.getMergedIntoFlatId());
            return keep != null && "BOOKED".equals(keep.getStatus());
        }
        return "BOOKED".equals(f.getStatus());
    }

    private static String resolveGridOwnerTitle(
            Flat f, Booking b, boolean bookable, Map<UUID, Flat> flatById) {
        if (FlatUnitTypes.isDuplexSecondary(f)) {
            Flat primary = flatById.get(f.getDuplexPrimaryFlatId());
            if (primary != null && "BOOKED".equals(primary.getStatus())) {
                return "Booked";
            }
            return primary != null ? "Duplex · " + primary.getFlatNumber() : "Duplex";
        }
        if (FlatUnitTypes.isMergeAbsorbed(f)) {
            Flat keep = flatById.get(f.getMergedIntoFlatId());
            if (keep != null && "BOOKED".equals(keep.getStatus())) {
                return "Booked";
            }
            return keep != null ? "Merged · " + keep.getFlatNumber() : "Merged";
        }
        if (bookable) {
            return ownerCardTitle(f, b);
        }
        return "BOOKED".equals(f.getStatus()) ? "Booked" : "";
    }

    private static String resolveLinkedUnitDetail(Flat f, Map<UUID, Flat> flatById, Booking b) {
        if (FlatUnitTypes.isDuplexSecondary(f)) {
            Flat primary = flatById.get(f.getDuplexPrimaryFlatId());
            if (primary == null) {
                return "Linked duplex";
            }
            if (b != null && "BOOKED".equals(primary.getStatus())) {
                return "Via " + primary.getFlatNumber();
            }
            return "With " + primary.getFlatNumber();
        }
        if (FlatUnitTypes.isMergeAbsorbed(f)) {
            Flat keep = flatById.get(f.getMergedIntoFlatId());
            if (keep == null) {
                return "Linked merge";
            }
            if (b != null && "BOOKED".equals(keep.getStatus())) {
                return "Via " + keep.getFlatNumber();
            }
            return "With " + keep.getFlatNumber();
        }
        return "";
    }

    private static String buildLinkedUnitTooltip(Flat f, Map<UUID, Flat> flatById, Booking b) {
        if (FlatUnitTypes.isDuplexSecondary(f) && b != null) {
            Flat primary = flatById.get(f.getDuplexPrimaryFlatId());
            if (primary != null && "BOOKED".equals(primary.getStatus())) {
                return buildBuyerTooltip(primary, b);
            }
        }
        if (FlatUnitTypes.isMergeAbsorbed(f) && b != null) {
            Flat keep = flatById.get(f.getMergedIntoFlatId());
            if (keep != null && "BOOKED".equals(keep.getStatus())) {
                return buildBuyerTooltip(keep, b);
            }
        }
        return "";
    }

    private static String resolveDuplexSecondaryDetail(Flat f, Map<UUID, Flat> flatById, Booking b) {
        return resolveLinkedUnitDetail(f, flatById, b);
    }

    private static String buildDuplexSecondaryTooltip(Flat f, Map<UUID, Flat> flatById, Booking b) {
        return buildLinkedUnitTooltip(f, flatById, b);
    }

    private static String duplexPartnerFlatNumber(Flat f, Map<UUID, Flat> flatById) {
        if (FlatUnitTypes.isDuplexSecondary(f) && f.getDuplexPrimaryFlatId() != null) {
            Flat primary = flatById.get(f.getDuplexPrimaryFlatId());
            return primary != null ? primary.getFlatNumber() : null;
        }
        if (FlatUnitTypes.isDuplexPrimary(f) && f.getDuplexSecondaryFlatId() != null) {
            Flat secondary = flatById.get(f.getDuplexSecondaryFlatId());
            return secondary != null ? secondary.getFlatNumber() : null;
        }
        return null;
    }

    private static String gridTypeLabel(Flat flat, Map<UUID, Flat> flatById) {
        if (FlatUnitTypes.isDuplexSecondary(flat)) {
            Flat primary = flatById.get(flat.getDuplexPrimaryFlatId());
            return primary != null ? "Duplex · " + primary.getFlatNumber() : "Duplex";
        }
        if (FlatUnitTypes.isMergeAbsorbed(flat)) {
            Flat keep = flatById.get(flat.getMergedIntoFlatId());
            return keep != null ? "Merged · " + keep.getFlatNumber() : "Merged";
        }
        if (FlatUnitTypes.isDuplexPrimary(flat)) {
            Flat secondary = flatById.get(flat.getDuplexSecondaryFlatId());
            if (secondary != null) {
                int lo = Math.min(flat.getFloorNumber(), secondary.getFloorNumber());
                int hi = Math.max(flat.getFloorNumber(), secondary.getFloorNumber());
                return "DUPLEX " + lo + "–" + hi;
            }
        }
        if (FlatUnitTypes.isMergePrimary(flat)) {
            Flat absorbed = flatById.get(flat.getMergedAbsorbedFlatId());
            if (absorbed != null) {
                return "MERGED · " + flat.getFlatNumber() + "+" + absorbed.getFlatNumber();
            }
        }
        return flat.getBhkType();
    }

    private Flat requireFlatForAdmin(UUID flatId) {
        Flat flat =
                flatRepository
                        .findByIdWithBuilding(flatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        buildingService.resolveForAccess(flat.getBuilding().getId());
        return flat;
    }

    private Flat requireResidentialFlatForAdmin(UUID flatId) {
        Flat flat = requireFlatForAdmin(flatId);
        if (FlatUnitTypes.isNonBookable(flat) && !FlatUnitTypes.isDuplexPrimary(flat)) {
            throw new IllegalArgumentException("Use unit type edit for parking and amenity slots.");
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
