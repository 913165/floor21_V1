package com.floor21.service;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.dto.FlatAddToFloorDto;
import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.FlatGridFlatDto;
import com.floor21.dto.FlatGridFloorDto;
import com.floor21.dto.FlatMergeCandidateDto;
import com.floor21.dto.FlatMergeDto;
import com.floor21.dto.FloorMergeSplitResult;
import com.floor21.dto.LinkedParkingSlotDto;
import com.floor21.dto.ParkingFixturePlacementDto;
import com.floor21.dto.ParkingFloorConfigDto;
import com.floor21.dto.ParkingGridPlacementDto;
import com.floor21.dto.ParkingGridColDto;
import com.floor21.dto.ParkingGridRowDto;
import com.floor21.dto.ParkingLayoutDto;
import com.floor21.dto.ParkingLinkDto;
import com.floor21.dto.ParkingPlanDto;
import com.floor21.dto.ParkingResidentialOptionDto;
import com.floor21.dto.ParkingSlotOptionDto;
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
import com.floor21.util.ParkingFloorConfigUtil;
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
            boolean parkingSection =
                    !cells.isEmpty() && cells.stream().allMatch(FlatGridFlatDto::parking);
            int parkingSlotCount = parkingSection ? cells.size() : 0;
            String parkingRangeLabel = parkingSection ? parkingRangeLabel(cells) : null;
            boolean parkingConfigured =
                    parkingSection && ParkingFloorConfigUtil.isConfigured(building, floor);
            int parkingCarSizePercent =
                    parkingConfigured
                            ? ParkingFloorConfigUtil.resolveCarSizePercent(
                                    ParkingFloorConfigUtil.forFloor(building, floor))
                            : ParkingFloorConfigUtil.DEFAULT_CAR_SIZE_PERCENT;
            int slotCountForMin = parkingSlotCount > 0 ? parkingSlotCount : 1;
            int parkingMinGridRows =
                    ParkingFloorConfigUtil.minGridRowsForSlotCount(slotCountForMin);
            ParkingFloorConfigUtil.FloorConfig parkingConfig =
                    ParkingFloorConfigUtil.forFloor(building, floor);
            int parkingGridRows =
                    parkingConfigured
                            ? (parkingConfig.gridRows() != null
                                    ? parkingConfig.gridRows()
                                    : parkingMinGridRows)
                            : parkingMinGridRows;
            rows.add(
                    new FlatGridFloorDto(
                            floor,
                            "Floor " + floor,
                            cells,
                            parkingSection,
                            parkingSlotCount,
                            parkingRangeLabel,
                            parkingConfigured,
                            parkingCarSizePercent,
                            parkingGridRows,
                            parkingMinGridRows,
                            parkingConfigured ? parkingConfig.resolvedCarLiftCount() : 0,
                            parkingConfigured ? parkingConfig.resolvedPassengerLiftCount() : 0,
                            parkingConfigured ? parkingConfig.resolvedGateCount() : 0,
                            parkingConfigured
                                    ? ParkingFloorConfigUtil.resolveSlotAreaSqft(parkingConfig)
                                    : null));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public ParkingPlanDto getParkingPlan(UUID buildingId, int floorNumber) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        if (!ParkingFloorConfigUtil.isConfigured(building, floorNumber)) {
            throw new IllegalArgumentException(
                    "Parking is not configured for floor " + floorNumber + " yet.");
        }
        UUID builderId = building.getBuilder().getId();
        List<Flat> flats =
                flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                        buildingId, builderId, floorNumber);
        if (flats.isEmpty()
                || flats.stream().anyMatch(f -> !FlatUnitTypes.isParkingCode(f.getBhkType()))) {
            throw new IllegalArgumentException("No parking layout on floor " + floorNumber + ".");
        }
        return buildParkingPlan(building, floorNumber, flats);
    }

    @Transactional
    public ParkingPlanDto saveParkingLayout(
            UUID buildingId, int floorNumber, ParkingLayoutDto dto) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        if (!ParkingFloorConfigUtil.isConfigured(building, floorNumber)) {
            throw new IllegalArgumentException(
                    "Parking is not configured for floor " + floorNumber + " yet.");
        }
        ParkingFloorConfigUtil.FloorConfig config =
                ParkingFloorConfigUtil.forFloor(building, floorNumber);
        validateParkingLayout(dto, config.slotCount(), config);
        List<ParkingFloorConfigUtil.GridPlacement> placements =
                dto.placements().stream()
                        .map(
                                p ->
                                        new ParkingFloorConfigUtil.GridPlacement(
                                                p.slotNumber(),
                                                p.col(),
                                                p.row(),
                                                normalizeParkingOrientation(p.orientation())))
                        .toList();
        List<ParkingFloorConfigUtil.FixturePlacement> fixtures =
                mapFixturePlacements(dto.fixtures(), config);
        ParkingFloorConfigUtil.saveLayout(
                building, floorNumber, dto.gridCols(), dto.gridRows(), placements, fixtures);
        buildingRepository.save(building);
        return getParkingPlan(buildingId, floorNumber);
    }

    @Transactional
    public ParkingPlanDto adjustParkingGridRow(
            UUID buildingId, int floorNumber, ParkingGridRowDto dto) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        if (!ParkingFloorConfigUtil.isConfigured(building, floorNumber)) {
            throw new IllegalArgumentException(
                    "Parking is not configured for floor " + floorNumber + " yet.");
        }
        ParkingFloorConfigUtil.FloorConfig config =
                ParkingFloorConfigUtil.forFloor(building, floorNumber);
        int gridCols =
                config.gridCols() != null
                        ? config.gridCols()
                        : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;
        int gridRows =
                config.gridRows() != null
                        ? config.gridRows()
                        : ParkingFloorConfigUtil.minGridRowsForSlotCount(config.slotCount());
        List<ParkingFloorConfigUtil.GridPlacement> source =
                config.placements() != null && !config.placements().isEmpty()
                        ? config.placements()
                        : ParkingFloorConfigUtil.defaultGridPlacements(
                                config.slotCount(), gridCols, gridRows);
        List<ParkingFloorConfigUtil.FixturePlacement> fixtureSource =
                config.fixtures() != null ? config.fixtures() : List.of();
        ParkingFloorConfigUtil.GridRowAdjustResult adjusted =
                ParkingFloorConfigUtil.adjustGridRows(
                        config.slotCount(), gridRows, source, fixtureSource, dto.action());
        ParkingFloorConfigUtil.saveLayout(
                building,
                floorNumber,
                gridCols,
                adjusted.gridRows(),
                adjusted.placements(),
                adjusted.fixtures());
        buildingRepository.save(building);
        return getParkingPlan(buildingId, floorNumber);
    }

    @Transactional
    public ParkingPlanDto adjustParkingGridCol(
            UUID buildingId, int floorNumber, ParkingGridColDto dto) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        if (!ParkingFloorConfigUtil.isConfigured(building, floorNumber)) {
            throw new IllegalArgumentException(
                    "Parking is not configured for floor " + floorNumber + " yet.");
        }
        ParkingFloorConfigUtil.FloorConfig config =
                ParkingFloorConfigUtil.forFloor(building, floorNumber);
        int gridCols =
                config.gridCols() != null
                        ? config.gridCols()
                        : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;
        int gridRows =
                config.gridRows() != null
                        ? config.gridRows()
                        : ParkingFloorConfigUtil.minGridRowsForSlotCount(config.slotCount());
        List<ParkingFloorConfigUtil.GridPlacement> source =
                config.placements() != null && !config.placements().isEmpty()
                        ? config.placements()
                        : ParkingFloorConfigUtil.defaultGridPlacements(
                                config.slotCount(), gridCols, gridRows);
        List<ParkingFloorConfigUtil.FixturePlacement> fixtureSource =
                config.fixtures() != null ? config.fixtures() : List.of();
        ParkingFloorConfigUtil.GridColAdjustResult adjusted =
                ParkingFloorConfigUtil.adjustGridCols(
                        config.slotCount(), gridCols, source, fixtureSource, dto.action());
        ParkingFloorConfigUtil.saveLayout(
                building,
                floorNumber,
                adjusted.gridCols(),
                gridRows,
                adjusted.placements(),
                adjusted.fixtures());
        buildingRepository.save(building);
        return getParkingPlan(buildingId, floorNumber);
    }

    @Transactional
    public ParkingPlanDto configureParkingFloor(
            UUID buildingId, int floorNumber, ParkingFloorConfigDto dto) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        int slotCount = dto.slotCount();
        Building building = buildingService.resolveForAccess(buildingId);
        int parkingFloors = building.getParkingFloors() != null ? building.getParkingFloors() : 0;
        if (floorNumber > parkingFloors) {
            throw new IllegalArgumentException("Floor " + floorNumber + " is not a parking floor.");
        }
        UUID builderId = building.getBuilder().getId();
        List<Flat> existing =
                new ArrayList<>(
                        flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                                buildingId, builderId, floorNumber));
        if (existing.stream()
                .anyMatch(
                        f ->
                                !FlatUnitTypes.isParkingCode(f.getBhkType())
                                        && !Boolean.TRUE.equals(f.getParking()))) {
            throw new IllegalArgumentException("Floor " + floorNumber + " contains non-parking units.");
        }
        Builder builder = building.getBuilder();
        Instant now = Instant.now();
        while (existing.size() > slotCount) {
            Flat last = existing.remove(existing.size() - 1);
            flatRepository.delete(last);
        }
        flatRepository.flush();
        BigDecimal slotAreaSqft =
                ParkingFloorConfigUtil.normalizeSlotAreaSqft(dto.slotAreaSqft());
        int priorCount = existing.size();
        while (existing.size() < slotCount) {
            int unit = existing.size() + 1;
            Flat created = parkingFlat(builder, building, floorNumber, unit, now);
            created.setAreaSqft(slotAreaSqft);
            existing.add(flatRepository.save(created));
        }
        for (int i = 0; i < existing.size(); i++) {
            Flat flat = existing.get(i);
            int unit = i + 1;
            flat.setUnitNumber(unit);
            flat.setFlatNumber(String.format("%02d%02d", floorNumber, unit));
            if (i >= priorCount) {
                flat.setAreaSqft(slotAreaSqft);
            }
            flatRepository.save(flat);
        }
        int carSizePercent =
                ParkingFloorConfigUtil.normalizeCarSizePercent(dto.carSizePercent());
        int carLiftCount =
                ParkingFloorConfigUtil.resolveCarLiftCountFromDto(
                        dto.carLiftCount(), dto.liftCount(), dto.showLift());
        int passengerLiftCount =
                ParkingFloorConfigUtil.resolvePassengerLiftCountFromDto(dto.passengerLiftCount());
        int gateCount =
                ParkingFloorConfigUtil.resolveGateCountFromDto(dto.gateCount(), dto.showGate());
        ParkingFloorConfigUtil.markConfigured(
                building,
                floorNumber,
                slotCount,
                carSizePercent,
                null,
                carLiftCount,
                passengerLiftCount,
                gateCount,
                slotAreaSqft);
        buildingRepository.save(building);
        return buildParkingPlan(building, floorNumber, existing);
    }

    @Transactional(readOnly = true)
    public List<ParkingResidentialOptionDto> listResidentialFlatsForParkingLink(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        return flatRepository.findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, builderId)
                .stream()
                .filter(this::isLinkableResidentialFlat)
                .sorted(
                        Comparator.comparing(Flat::getFloorNumber)
                                .thenComparing(Flat::getUnitNumber))
                .map(
                        f ->
                                new ParkingResidentialOptionDto(
                                        f.getId(),
                                        f.getFlatNumber(),
                                        f.getFloorNumber(),
                                        f.getBhkType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedParkingSlotDto> listLinkedParkingForResidentialFlat(UUID residentialFlatId) {
        Flat residential =
                flatRepository
                        .findByIdWithBuilding(residentialFlatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        buildingService.resolveForAccess(residential.getBuilding().getId());
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
    public List<ParkingSlotOptionDto> listParkingSlotsForLink(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        int parkingFloors = building.getParkingFloors() != null ? building.getParkingFloors() : 0;
        List<Flat> parkingFlats = new ArrayList<>();
        for (int floor = 1; floor <= parkingFloors; floor++) {
            if (!ParkingFloorConfigUtil.isConfigured(building, floor)) {
                continue;
            }
            List<Flat> flats =
                    flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                            buildingId, builderId, floor);
            for (Flat flat : flats) {
                if (FlatUnitTypes.isParkingCode(flat.getBhkType())
                        || Boolean.TRUE.equals(flat.getParking())) {
                    parkingFlats.add(flat);
                }
            }
        }
        java.util.Set<UUID> linkedIds =
                parkingFlats.stream()
                        .map(Flat::getLinkedResidentialFlatId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<UUID, Flat> linkedById =
                linkedIds.isEmpty()
                        ? Map.of()
                        : flatRepository.findAllById(linkedIds).stream()
                                .collect(Collectors.toMap(Flat::getId, f -> f));
        return parkingFlats.stream()
                .sorted(
                        Comparator.comparing(Flat::getFloorNumber)
                                .thenComparing(Flat::getUnitNumber))
                .map(
                        p -> {
                            UUID linkedId = p.getLinkedResidentialFlatId();
                            String linkedNumber = null;
                            if (linkedId != null) {
                                Flat linked = linkedById.get(linkedId);
                                if (linked != null) {
                                    linkedNumber = linked.getFlatNumber();
                                }
                            }
                            return new ParkingSlotOptionDto(
                                    p.getId(),
                                    p.getFlatNumber(),
                                    p.getFloorNumber(),
                                    p.getUnitNumber() != null ? p.getUnitNumber() : 0,
                                    linkedId,
                                    linkedNumber);
                        })
                .toList();
    }

    @Transactional
    public ParkingPlanDto.ParkingPlanSlotDto linkParkingToResidential(
            UUID parkingFlatId, ParkingLinkDto dto) {
        Flat parking = requireFlatForAdmin(parkingFlatId);
        if (!FlatUnitTypes.isParkingCode(parking.getBhkType())
                && !Boolean.TRUE.equals(parking.getParking())) {
            throw new IllegalArgumentException("Only parking slots can be linked to a flat.");
        }
        UUID residentialId = dto != null ? dto.residentialFlatId() : null;
        String linkedNumber = null;
        if (residentialId == null) {
            parking.setLinkedResidentialFlatId(null);
        } else {
            Flat residential = requireLinkableResidentialFlat(residentialId);
            if (!residential.getBuilding().getId().equals(parking.getBuilding().getId())) {
                throw new IllegalArgumentException("Residential flat must be in the same building.");
            }
            parking.setLinkedResidentialFlatId(residentialId);
            linkedNumber = residential.getFlatNumber();
        }
        flatRepository.save(parking);
        int slotNumber =
                parking.getUnitNumber() != null && parking.getUnitNumber() > 0
                        ? parking.getUnitNumber()
                        : 0;
        return new ParkingPlanDto.ParkingPlanSlotDto(
                slotNumber,
                parking.getId(),
                parking.getFlatNumber(),
                residentialId,
                linkedNumber,
                parking.getAreaSqft());
    }

    private boolean isLinkableResidentialFlat(Flat flat) {
        if (flat == null) {
            return false;
        }
        if (FlatUnitTypes.isParkingCode(flat.getBhkType())
                || Boolean.TRUE.equals(flat.getParking())) {
            return false;
        }
        if (FlatUnitTypes.isAmenityCode(flat.getBhkType())) {
            return false;
        }
        if (FlatUnitTypes.isMergeAbsorbed(flat)) {
            return false;
        }
        if (FlatUnitTypes.isDuplexSecondary(flat)) {
            return false;
        }
        return true;
    }

    private Flat requireLinkableResidentialFlat(UUID flatId) {
        Flat flat = requireFlatForAdmin(flatId);
        if (!isLinkableResidentialFlat(flat)) {
            throw new IllegalArgumentException("Choose a residential flat to link parking.");
        }
        return flat;
    }

    private ParkingPlanDto buildParkingPlan(Building building, int floorNumber, List<Flat> flats) {
        int n = flats.size();
        int bottomCount = (int) Math.ceil(n / 2.0);
        List<Integer> bottomRow = new ArrayList<>();
        for (int slot = 1; slot <= bottomCount; slot++) {
            bottomRow.add(slot);
        }
        List<Integer> topRow = new ArrayList<>();
        for (int slot = n; slot > bottomCount; slot--) {
            topRow.add(slot);
        }
        ParkingFloorConfigUtil.FloorConfig config =
                ParkingFloorConfigUtil.forFloor(building, floorNumber);
        int gridCols =
                config.gridCols() != null
                        ? config.gridCols()
                        : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;
        int minGridRows = ParkingFloorConfigUtil.minGridRowsForSlotCount(n);
        int gridRows =
                config.gridRows() != null ? config.gridRows() : minGridRows;
        if (gridRows < minGridRows) {
            gridRows = minGridRows;
        }
        List<ParkingGridPlacementDto> placements =
                resolveGridPlacements(config, n, gridCols, gridRows);
        List<ParkingPlanDto.ParkingPlanSlotDto> slots = new ArrayList<>();
        java.util.Set<UUID> linkedIds =
                flats.stream()
                        .map(Flat::getLinkedResidentialFlatId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<UUID, Flat> linkedById =
                linkedIds.isEmpty()
                        ? Map.of()
                        : flatRepository.findAllById(linkedIds).stream()
                                .collect(Collectors.toMap(Flat::getId, f -> f));
        for (int i = 0; i < flats.size(); i++) {
            Flat parking = flats.get(i);
            UUID linkedId = parking.getLinkedResidentialFlatId();
            String linkedNumber = null;
            if (linkedId != null) {
                Flat linked = linkedById.get(linkedId);
                if (linked != null) {
                    linkedNumber = linked.getFlatNumber();
                }
            }
            slots.add(
                    new ParkingPlanDto.ParkingPlanSlotDto(
                            i + 1,
                            parking.getId(),
                            parking.getFlatNumber(),
                            linkedId,
                            linkedNumber,
                            parking.getAreaSqft()));
        }
        int carSizePercent = ParkingFloorConfigUtil.resolveCarSizePercent(config);
        List<ParkingFixturePlacementDto> fixtureDtos = toFixtureDtos(config);
        return new ParkingPlanDto(
                floorNumber,
                n,
                topRow,
                bottomRow,
                slots,
                gridCols,
                gridRows,
                placements,
                true,
                carSizePercent,
                minGridRows,
                config.resolvedCarLiftCount(),
                config.resolvedPassengerLiftCount(),
                config.resolvedLiftCount(),
                config.resolvedGateCount(),
                fixtureDtos);
    }

    private static List<ParkingFixturePlacementDto> toFixtureDtos(
            ParkingFloorConfigUtil.FloorConfig config) {
        if (config.fixtures() == null || config.fixtures().isEmpty()) {
            return List.of();
        }
        List<ParkingFixturePlacementDto> out = new ArrayList<>();
        for (ParkingFloorConfigUtil.FixturePlacement f : config.fixtures()) {
            out.add(
                    new ParkingFixturePlacementDto(
                            ParkingFloorConfigUtil.normalizeFixtureKind(f.kind()),
                            f.index(),
                            f.col(),
                            f.row(),
                            normalizeParkingOrientation(f.orientation())));
        }
        return out;
    }

    private List<ParkingFloorConfigUtil.FixturePlacement> mapFixturePlacements(
            List<ParkingFixturePlacementDto> fixtures, ParkingFloorConfigUtil.FloorConfig config) {
        if (fixtures == null || fixtures.isEmpty()) {
            int cols =
                    config.gridCols() != null
                            ? config.gridCols()
                            : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;
            int rows =
                    config.gridRows() != null
                            ? config.gridRows()
                            : ParkingFloorConfigUtil.minGridRowsForSlotCount(config.slotCount());
            List<ParkingFloorConfigUtil.GridPlacement> carPlacements =
                    config.placements() != null && !config.placements().isEmpty()
                            ? config.placements()
                            : ParkingFloorConfigUtil.defaultGridPlacements(
                                    config.slotCount(), cols, rows);
            return ParkingFloorConfigUtil.defaultFixtures(
                    config.resolvedCarLiftCount(),
                    config.resolvedPassengerLiftCount(),
                    config.resolvedGateCount(),
                    cols,
                    rows,
                    carPlacements);
        }
        return fixtures.stream()
                .map(
                        f ->
                                new ParkingFloorConfigUtil.FixturePlacement(
                                        ParkingFloorConfigUtil.normalizeFixtureKind(f.kind()),
                                        f.index(),
                                        f.col(),
                                        f.row(),
                                        normalizeParkingOrientation(f.orientation())))
                .toList();
    }

    private static List<ParkingGridPlacementDto> resolveGridPlacements(
            ParkingFloorConfigUtil.FloorConfig config, int slotCount, int gridCols, int gridRows) {
        List<ParkingFloorConfigUtil.GridPlacement> source =
                config.placements() != null && !config.placements().isEmpty()
                        ? config.placements()
                        : ParkingFloorConfigUtil.defaultGridPlacements(slotCount, gridCols, gridRows);
        List<ParkingGridPlacementDto> out = new ArrayList<>();
        for (ParkingFloorConfigUtil.GridPlacement p : source) {
            out.add(
                    new ParkingGridPlacementDto(
                            p.slotNumber(),
                            p.col(),
                            p.row(),
                            normalizeParkingOrientation(p.orientation())));
        }
        return out;
    }

    private static void validateParkingLayout(
            ParkingLayoutDto dto, int slotCount, ParkingFloorConfigUtil.FloorConfig config) {
        if (dto.placements().size() != slotCount) {
            throw new IllegalArgumentException(
                    "Layout must include exactly " + slotCount + " parking slots.");
        }
        java.util.Set<Integer> slotNumbers = new java.util.HashSet<>();
        for (ParkingGridPlacementDto p : dto.placements()) {
            if (p.slotNumber() < 1 || p.slotNumber() > slotCount) {
                throw new IllegalArgumentException("Invalid slot number: " + p.slotNumber());
            }
            if (!slotNumbers.add(p.slotNumber())) {
                throw new IllegalArgumentException("Duplicate slot number: " + p.slotNumber());
            }
            normalizeParkingOrientation(p.orientation());
        }
        List<ParkingFixturePlacementDto> fixtures =
                dto.fixtures() != null ? dto.fixtures() : List.of();
        int carLiftSeen = 0;
        int passengerLiftSeen = 0;
        int gateSeen = 0;
        for (ParkingFixturePlacementDto f : fixtures) {
            String kind = ParkingFloorConfigUtil.normalizeFixtureKind(f.kind());
            switch (kind) {
                case "CAR_LIFT" -> carLiftSeen++;
                case "PASSENGER_LIFT" -> passengerLiftSeen++;
                case "GATE" -> gateSeen++;
                default -> {}
            }
        }
        if (carLiftSeen != config.resolvedCarLiftCount()) {
            throw new IllegalArgumentException(
                    "Layout must include exactly "
                            + config.resolvedCarLiftCount()
                            + " car lift(s).");
        }
        if (passengerLiftSeen != config.resolvedPassengerLiftCount()) {
            throw new IllegalArgumentException(
                    "Layout must include exactly "
                            + config.resolvedPassengerLiftCount()
                            + " passenger lift(s).");
        }
        if (gateSeen != config.resolvedGateCount()) {
            throw new IllegalArgumentException(
                    "Layout must include exactly "
                            + config.resolvedGateCount()
                            + " gate(s).");
        }
        List<ParkingFloorConfigUtil.GridPlacement> placements =
                dto.placements().stream()
                        .map(
                                p ->
                                        new ParkingFloorConfigUtil.GridPlacement(
                                                p.slotNumber(),
                                                p.col(),
                                                p.row(),
                                                normalizeParkingOrientation(p.orientation())))
                        .toList();
        List<ParkingFloorConfigUtil.FixturePlacement> fixturePlacements =
                fixtures.stream()
                        .map(
                                f ->
                                        new ParkingFloorConfigUtil.FixturePlacement(
                                                ParkingFloorConfigUtil.normalizeFixtureKind(
                                                        f.kind()),
                                                f.index(),
                                                f.col(),
                                                f.row(),
                                                normalizeParkingOrientation(f.orientation())))
                        .toList();
        ParkingFloorConfigUtil.assertLayoutValid(
                placements, fixturePlacements, dto.gridRows(), dto.gridCols(), slotCount);
    }

    private static String normalizeParkingOrientation(String orientation) {
        if (orientation == null || orientation.isBlank()) {
            return "vertical";
        }
        String value = orientation.trim().toLowerCase(Locale.ROOT);
        if ("vertical".equals(value) || "v".equals(value)) {
            return "vertical";
        }
        if ("horizontal".equals(value) || "h".equals(value)) {
            return "horizontal";
        }
        throw new IllegalArgumentException("Orientation must be vertical or horizontal.");
    }

    private static String parkingRangeLabel(List<FlatGridFlatDto> cells) {
        if (cells.isEmpty()) {
            return "";
        }
        String first = cells.get(0).flatNumber();
        String last = cells.get(cells.size() - 1).flatNumber();
        return cells.size() == 1 ? first : first + "–" + last;
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
        ParkingFloorConfigUtil.clearAll(building);
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
        f.setAreaSqft(ParkingFloorConfigUtil.DEFAULT_SLOT_AREA_SQFT);
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
        if (hasActiveBooking(flatId)) {
            if (dto.bhkType() != null && !dto.bhkType().isBlank()) {
                String requested = FlatUnitTypes.normalize(dto.bhkType());
                if (!requested.equals(flat.getBhkType())) {
                    throw new IllegalArgumentException(
                            "Cannot change unit type while an active booking exists. You can still update areas and price.");
                }
            }
            FlatUnitTypes.applyBookedFlatAdjustments(
                    flat, dto.areaSqft(), dto.carpetAreaSqft(), dto.balconyAreaSqft(), dto.basePrice());
        } else {
            FlatUnitTypes.applyToFlat(
                    flat, dto.bhkType(), dto.areaSqft(), dto.carpetAreaSqft(), dto.balconyAreaSqft(), dto.basePrice());
        }
        flat = flatRepository.saveAndFlush(flat);
        return flat;
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
            FlatUnitTypes.applyToFlat(
                    flat,
                    dto.bhkType(),
                    dto.areaSqft(),
                    dto.carpetAreaSqft(),
                    dto.balconyAreaSqft(),
                    dto.basePrice());
        }
        return flatRepository.saveAll(flats);
    }

    @Transactional
    public void deleteFlatAsPlatformAdmin(UUID flatId) {
        Flat flat = requireResidentialFlatForAdmin(flatId);
        assertNotInDuplex(flat);
        assertNotMerged(flat);
        assertNoActiveBooking(flatId, "Cannot remove a flat that has an active booking.");
        if ("BOOKED".equals(flat.getStatus())) {
            throw new IllegalArgumentException("Cannot remove a booked flat. Cancel the booking first.");
        }
        partnerFlatAllocationService.clearAssignmentForFlat(flatId);
        flatRepository.delete(flat);
    }

    /**
     * Adds one new unit slot to an existing floor row (e.g. after deleting a flat). Uses the next unit
     * number on that floor; flat number is {@code floor + unit} (e.g. 0305).
     */
    @Transactional
    public Flat addFlatToFloorAsPlatformAdmin(UUID buildingId, FlatAddToFloorDto dto) {
        if (dto.floorNumber() == null || dto.floorNumber() < 1) {
            throw new IllegalArgumentException("Floor number is required.");
        }
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        int floorNumber = dto.floorNumber();
        if (building.getTotalFloors() == null || floorNumber > building.getTotalFloors()) {
            throw new IllegalArgumentException(
                    "Floor must be between 1 and " + building.getTotalFloors() + ".");
        }
        List<Flat> onFloor =
                flatRepository.findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
                        buildingId, builderId, floorNumber);
        if (onFloor.isEmpty()) {
            throw new IllegalArgumentException(
                    "Floor "
                            + floorNumber
                            + " has no units yet. Use Generate flats or Add floors first.");
        }
        String bhk = FlatUnitTypes.normalize(dto.bhkType());
        if (bhk == null || bhk.isBlank()) {
            throw new IllegalArgumentException("Unit type is required.");
        }
        int nextUnit = onFloor.stream().mapToInt(Flat::getUnitNumber).max().orElse(0) + 1;
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();
        Flat flat = new Flat();
        flat.setBuilder(builder);
        flat.setBuilding(building);
        flat.setFloorNumber(floorNumber);
        flat.setUnitNumber(nextUnit);
        flat.setFlatNumber(String.format("%02d%02d", floorNumber, nextUnit));
        flat.setCreatedAt(now);
        flat.setStatus("AVAILABLE");
        FlatUnitTypes.applyToFlat(
                flat, bhk, dto.areaSqft(), dto.carpetAreaSqft(), dto.balconyAreaSqft(), dto.basePrice());
        return flatRepository.save(flat);
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
        if (keep.getPreMergeCarpetAreaSqft() != null) {
            keep.setCarpetAreaSqft(keep.getPreMergeCarpetAreaSqft());
        }
        if (keep.getPreMergeBalconyAreaSqft() != null) {
            keep.setBalconyAreaSqft(keep.getPreMergeBalconyAreaSqft());
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
        keep.setPreMergeCarpetAreaSqft(null);
        keep.setPreMergeBalconyAreaSqft(null);
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
        keep.setPreMergeCarpetAreaSqft(keep.getCarpetAreaSqft());
        keep.setPreMergeBalconyAreaSqft(keep.getBalconyAreaSqft());
        keep.setPreMergeBasePrice(keep.getBasePrice());
        keep.setPreMergeStatus(keep.getStatus());
        remove.setPreMergeStatus(remove.getStatus());
        keep.setMergedAbsorbedFlatId(remove.getId());
        remove.setMergedIntoFlatId(keep.getId());
        applyMergedDetails(keep, dto, false, null, null, null, null);
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
        if (secondary.getCarpetAreaSqft() != null) {
            primary.setCarpetAreaSqft(secondary.getCarpetAreaSqft());
        }
        if (secondary.getBalconyAreaSqft() != null) {
            primary.setBalconyAreaSqft(secondary.getBalconyAreaSqft());
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
        BigDecimal defaultCarpet = sumNullable(lower.getCarpetAreaSqft(), upper.getCarpetAreaSqft());
        BigDecimal defaultBalcony = sumNullable(lower.getBalconyAreaSqft(), upper.getBalconyAreaSqft());
        BigDecimal defaultPrice = sumNullable(lower.getBasePrice(), upper.getBasePrice());
        applyMergedDetails(lower, dto, true, defaultArea, defaultCarpet, defaultBalcony, defaultPrice);
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
            BigDecimal defaultCarpet,
            BigDecimal defaultBalcony,
            BigDecimal defaultPrice) {
        String bhk =
                dto.bhkType() != null && !dto.bhkType().isBlank()
                        ? normalizeBhkType(dto.bhkType())
                        : (verticalDuplex ? "DUPLEX" : target.getBhkType());
        target.setBhkType(bhk);
        BigDecimal area = dto.areaSqft() != null ? dto.areaSqft() : defaultArea;
        if (area != null) {
            if (area.signum() <= 0) {
                throw new IllegalArgumentException("Super built-up area must be greater than zero.");
            }
            target.setAreaSqft(area);
        }
        BigDecimal carpet = dto.carpetAreaSqft() != null ? dto.carpetAreaSqft() : defaultCarpet;
        if (carpet != null) {
            if (carpet.signum() <= 0) {
                throw new IllegalArgumentException("Carpet area must be greater than zero.");
            }
            target.setCarpetAreaSqft(carpet);
        }
        BigDecimal balcony = dto.balconyAreaSqft() != null ? dto.balconyAreaSqft() : defaultBalcony;
        if (balcony != null) {
            if (balcony.signum() < 0) {
                throw new IllegalArgumentException("Balcony area cannot be negative.");
            }
            target.setBalconyAreaSqft(balcony);
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
                f.getCarpetAreaSqft(),
                f.getBalconyAreaSqft(),
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

    private boolean hasActiveBooking(UUID flatId) {
        return bookingRepository.countActiveByFlatId(flatId) > 0;
    }

    private void assertNoActiveBooking(UUID flatId, String message) {
        if (hasActiveBooking(flatId)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalizeBhkType(String bhkType) {
        return ResidentialBhkTypes.normalize(bhkType);
    }
}
