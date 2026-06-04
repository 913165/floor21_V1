package com.floor21.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.dto.ParkingGridColDto;
import com.floor21.dto.ParkingGridRowDto;
import com.floor21.entity.Building;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Per-floor parking slot configuration stored as JSON on {@link Building}. */
public final class ParkingFloorConfigUtil {

    public static final int DEFAULT_GRID_COLS = 14;
    public static final int DEFAULT_GRID_ROWS = 8;
    public static final int MAX_GRID_ROWS = 24;
    public static final int MAX_GRID_COLS = 40;
    public static final int DEFAULT_CAR_SIZE_PERCENT = 100;
    public static final int MAX_CAR_SIZE_PERCENT = 200;
    public static final int MAX_FIXTURES_PER_KIND = 8;

    private static final ObjectMapper JSON = new ObjectMapper();

    public record GridPlacement(int slotNumber, int col, int row, String orientation) {}

    public record FixturePlacement(String kind, int index, int col, int row, String orientation) {}

    public record FloorConfig(
            int slotCount,
            boolean configured,
            Integer gridCols,
            Integer gridRows,
            List<GridPlacement> placements,
            Integer carSizePercent,
            Integer liftCount,
            Integer carLiftCount,
            Integer passengerLiftCount,
            Integer gateCount,
            List<FixturePlacement> fixtures,
            Boolean showLift,
            Boolean showGate) {

        public FloorConfig(int slotCount, boolean configured) {
            this(
                    slotCount,
                    configured,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        public int resolvedCarLiftCount() {
            if (carLiftCount != null) {
                return clampFixtureCount(carLiftCount);
            }
            if (liftCount != null) {
                return clampFixtureCount(liftCount);
            }
            if (showLift != null) {
                return showLift ? 1 : 0;
            }
            return 1;
        }

        public int resolvedPassengerLiftCount() {
            if (passengerLiftCount != null) {
                return clampFixtureCount(passengerLiftCount);
            }
            return 0;
        }

        /** Total lifts (car + passenger) for API consumers that expect a single count. */
        public int resolvedLiftCount() {
            return resolvedCarLiftCount() + resolvedPassengerLiftCount();
        }

        public int resolvedGateCount() {
            if (gateCount != null) {
                return clampFixtureCount(gateCount);
            }
            if (showGate != null) {
                return showGate ? 1 : 0;
            }
            return 1;
        }
    }

    private ParkingFloorConfigUtil() {}

    public static int clampFixtureCount(int value) {
        return Math.max(0, Math.min(MAX_FIXTURES_PER_KIND, value));
    }

    public static int resolveCarLiftCountFromDto(
            Integer carLiftCount, Integer liftCount, Boolean showLift) {
        if (carLiftCount != null) {
            return clampFixtureCount(carLiftCount);
        }
        if (liftCount != null) {
            return clampFixtureCount(liftCount);
        }
        if (showLift != null) {
            return showLift ? 1 : 0;
        }
        return 1;
    }

    public static int resolvePassengerLiftCountFromDto(Integer passengerLiftCount) {
        if (passengerLiftCount != null) {
            return clampFixtureCount(passengerLiftCount);
        }
        return 0;
    }

    public static int resolveGateCountFromDto(Integer gateCount, Boolean showGate) {
        if (gateCount != null) {
            return clampFixtureCount(gateCount);
        }
        if (showGate != null) {
            return showGate ? 1 : 0;
        }
        return 1;
    }

    public static int minGridRowsForSlotCount(int slotCount) {
        if (slotCount <= 0) {
            return 1;
        }
        int bottomCount = (int) Math.ceil(slotCount / 2.0);
        int topCount = slotCount - bottomCount;
        if (topCount > 0 && bottomCount > 0) {
            return 3;
        }
        return 1;
    }

    public static int minGridColsForSlotCount(int slotCount) {
        if (slotCount <= 0) {
            return 1;
        }
        int bottomCount = (int) Math.ceil(slotCount / 2.0);
        int topCount = slotCount - bottomCount;
        return Math.max(1, Math.max(bottomCount, topCount));
    }

    public static int normalizeGridRows(int slotCount, Integer gridRows) {
        int min = minGridRowsForSlotCount(slotCount);
        int value = gridRows != null ? gridRows : min;
        if (value < min) {
            throw new IllegalArgumentException(
                    "Grid needs at least "
                            + min
                            + " rows for "
                            + slotCount
                            + " parking slots.");
        }
        if (value > MAX_GRID_ROWS) {
            throw new IllegalArgumentException(
                    "Grid can have at most " + MAX_GRID_ROWS + " rows.");
        }
        return value;
    }

    public static int normalizeCarSizePercent(Integer value) {
        if (value == null) {
            return DEFAULT_CAR_SIZE_PERCENT;
        }
        return Math.max(50, Math.min(MAX_CAR_SIZE_PERCENT, value));
    }

    public static int resolveCarSizePercent(FloorConfig config) {
        if (config == null) {
            return DEFAULT_CAR_SIZE_PERCENT;
        }
        return normalizeCarSizePercent(config.carSizePercent());
    }

    public static Map<Integer, FloorConfig> read(Building building) {
        if (building == null || building.getParkingFloorConfig() == null || building.getParkingFloorConfig().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, FloorConfig> raw =
                    JSON.readValue(building.getParkingFloorConfig(), new TypeReference<>() {});
            Map<Integer, FloorConfig> out = new LinkedHashMap<>();
            for (Map.Entry<String, FloorConfig> e : raw.entrySet()) {
                out.put(Integer.parseInt(e.getKey()), e.getValue());
            }
            return out;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static FloorConfig forFloor(Building building, int floorNumber) {
        return read(building).getOrDefault(floorNumber, new FloorConfig(0, false));
    }

    public static boolean isConfigured(Building building, int floorNumber) {
        return forFloor(building, floorNumber).configured();
    }

    public static List<GridPlacement> defaultGridPlacements(int slotCount, int gridCols, int gridRows) {
        int bottomCount = (int) Math.ceil(slotCount / 2.0);
        int topCount = slotCount - bottomCount;
        int topRowIdx = 0;
        int bottomRowIdx;
        if (topCount > 0 && bottomCount > 0) {
            bottomRowIdx = Math.min(gridRows - 1, 2);
            if (gridRows >= 3 && bottomRowIdx < 2) {
                bottomRowIdx = 2;
            } else if (bottomRowIdx <= topRowIdx) {
                bottomRowIdx = Math.min(gridRows - 1, topRowIdx + 2);
            }
        } else {
            bottomRowIdx = 0;
            topRowIdx = 0;
        }
        List<GridPlacement> out = new ArrayList<>();
        for (int slot = 1; slot <= bottomCount; slot++) {
            out.add(
                    new GridPlacement(
                            slot, Math.min(slot - 1, gridCols - 1), bottomRowIdx, "vertical"));
        }
        for (int i = 0; i < topCount; i++) {
            int slotNum = bottomCount + topCount - i;
            out.add(
                    new GridPlacement(
                            slotNum, Math.min(i, gridCols - 1), topRowIdx, "vertical"));
        }
        return out;
    }

    public static List<FixturePlacement> defaultFixtures(
            int carLiftCount,
            int passengerLiftCount,
            int gateCount,
            int gridCols,
            int gridRows,
            List<GridPlacement> placements) {
        if (carLiftCount == 0 && passengerLiftCount == 0 && gateCount == 0) {
            return List.of();
        }
        Set<String> used = occupiedCells(placements);
        List<FixturePlacement> out = new ArrayList<>();

        List<int[]> liftCandidates = new ArrayList<>();
        List<int[]> gateCandidates = new ArrayList<>();
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                String key = col + ":" + row;
                if (!used.contains(key)) {
                    liftCandidates.add(new int[] {col, row});
                    gateCandidates.add(new int[] {col, row});
                }
            }
        }
        liftCandidates.sort(Comparator.comparingInt((int[] c) -> c[0]).thenComparingInt(c -> c[1]));
        gateCandidates.sort(
                Comparator.comparingInt((int[] c) -> -c[0]).thenComparingInt(c -> c[1]));

        for (int i = 1; i <= carLiftCount; i++) {
            int[] cell = takeFirstFreeCell(liftCandidates, used);
            if (cell == null) {
                throw new IllegalArgumentException(
                        "Not enough empty grid cells for "
                                + carLiftCount
                                + " car lift(s). Add rows/columns or reduce fixture counts.");
            }
            out.add(new FixturePlacement("CAR_LIFT", i, cell[0], cell[1], "vertical"));
            used.add(cellKey(cell[0], cell[1]));
        }
        for (int i = 1; i <= passengerLiftCount; i++) {
            int[] cell = takeFirstFreeCell(liftCandidates, used);
            if (cell == null) {
                throw new IllegalArgumentException(
                        "Not enough empty grid cells for "
                                + passengerLiftCount
                                + " passenger lift(s). Add rows/columns or reduce fixture counts.");
            }
            out.add(new FixturePlacement("PASSENGER_LIFT", i, cell[0], cell[1], "vertical"));
            used.add(cellKey(cell[0], cell[1]));
        }
        for (int i = 1; i <= gateCount; i++) {
            int[] cell = takeFirstFreeCell(gateCandidates, used);
            if (cell == null) {
                throw new IllegalArgumentException(
                        "Not enough empty grid cells for "
                                + gateCount
                                + " gate(s). Add rows/columns or reduce lift/gate counts.");
            }
            out.add(new FixturePlacement("GATE", i, cell[0], cell[1], "vertical"));
            used.add(cellKey(cell[0], cell[1]));
        }
        return out;
    }

    private static Set<String> occupiedCells(List<GridPlacement> placements) {
        Set<String> cells = new HashSet<>();
        if (placements == null) {
            return cells;
        }
        for (GridPlacement placement : placements) {
            cells.add(cellKey(placement.col(), placement.row()));
        }
        return cells;
    }

    private static String cellKey(int col, int row) {
        return col + ":" + row;
    }

    private static boolean fixturesOverlapPlacements(
            List<FixturePlacement> fixtures, List<GridPlacement> placements) {
        if (fixtures == null || fixtures.isEmpty()) {
            return false;
        }
        Set<String> carCells = occupiedCells(placements);
        for (FixturePlacement fixture : fixtures) {
            if (carCells.contains(cellKey(fixture.col(), fixture.row()))) {
                return true;
            }
        }
        return false;
    }

    private static int[] takeFirstFreeCell(List<int[]> candidates, Set<String> used) {
        for (int[] cell : candidates) {
            String key = cellKey(cell[0], cell[1]);
            if (!used.contains(key)) {
                return cell;
            }
        }
        return null;
    }

    private static List<FixturePlacement> resolveFixturesForFloor(
            FloorConfig existing,
            int carLiftCount,
            int passengerLiftCount,
            int gateCount,
            int gridCols,
            int gridRows,
            List<GridPlacement> placements) {
        if (carLiftCount == 0 && passengerLiftCount == 0 && gateCount == 0) {
            return List.of();
        }
        if (existing != null
                && existing.fixtures() != null
                && !existing.fixtures().isEmpty()
                && existing.resolvedCarLiftCount() == carLiftCount
                && existing.resolvedPassengerLiftCount() == passengerLiftCount
                && existing.resolvedGateCount() == gateCount
                && fixturesFit(existing.fixtures(), gridRows, gridCols)
                && !fixturesOverlapPlacements(existing.fixtures(), placements)) {
            return List.copyOf(existing.fixtures());
        }
        return defaultFixtures(
                carLiftCount, passengerLiftCount, gateCount, gridCols, gridRows, placements);
    }

    public static void markConfigured(
            Building building,
            int floorNumber,
            int slotCount,
            int carSizePercent,
            Integer gridRowsParam,
            int carLiftCount,
            int passengerLiftCount,
            int gateCount) {
        Map<Integer, FloorConfig> map = new LinkedHashMap<>(read(building));
        FloorConfig existing = map.get(floorNumber);
        int gridCols =
                existing != null && existing.gridCols() != null
                        ? existing.gridCols()
                        : DEFAULT_GRID_COLS;
        int gridRows =
                existing != null && existing.gridRows() != null
                        ? normalizeGridRows(slotCount, existing.gridRows())
                        : normalizeGridRows(slotCount, gridRowsParam);
        List<GridPlacement> placements;
        if (existing != null
                && existing.configured()
                && existing.slotCount() == slotCount
                && existing.placements() != null
                && !existing.placements().isEmpty()
                && placementsFit(existing.placements(), gridRows, gridCols)) {
            placements = existing.placements();
        } else {
            placements = defaultGridPlacements(slotCount, gridCols, gridRows);
        }
        int normalizedCarLift = clampFixtureCount(carLiftCount);
        int normalizedPassengerLift = clampFixtureCount(passengerLiftCount);
        int normalizedGate = clampFixtureCount(gateCount);
        List<FixturePlacement> fixtures =
                resolveFixturesForFloor(
                        existing,
                        normalizedCarLift,
                        normalizedPassengerLift,
                        normalizedGate,
                        gridCols,
                        gridRows,
                        placements);
        map.put(
                floorNumber,
                new FloorConfig(
                        slotCount,
                        true,
                        gridCols,
                        gridRows,
                        placements,
                        normalizeCarSizePercent(carSizePercent),
                        null,
                        normalizedCarLift,
                        normalizedPassengerLift,
                        normalizedGate,
                        fixtures,
                        null,
                        null));
        building.setParkingFloorConfig(toJson(map));
    }

    public static void saveLayout(
            Building building,
            int floorNumber,
            int gridCols,
            int gridRows,
            List<GridPlacement> placements,
            List<FixturePlacement> fixtures) {
        Map<Integer, FloorConfig> map = new LinkedHashMap<>(read(building));
        FloorConfig existing = map.getOrDefault(floorNumber, new FloorConfig(0, false));
        int normalizedRows = normalizeGridRows(existing.slotCount(), gridRows);
        List<FixturePlacement> fixtureList =
                fixtures != null ? List.copyOf(fixtures) : List.of();
        assertLayoutValid(placements, fixtureList, normalizedRows, gridCols, existing.slotCount());
        map.put(
                floorNumber,
                new FloorConfig(
                        existing.slotCount(),
                        true,
                        gridCols,
                        normalizedRows,
                        List.copyOf(placements),
                        resolveCarSizePercent(existing),
                        existing.liftCount(),
                        existing.carLiftCount(),
                        existing.passengerLiftCount(),
                        existing.gateCount(),
                        fixtureList,
                        existing.showLift(),
                        existing.showGate()));
        building.setParkingFloorConfig(toJson(map));
    }

    public static void assertLayoutValid(
            List<GridPlacement> placements,
            List<FixturePlacement> fixtures,
            int gridRows,
            int gridCols,
            int slotCount) {
        if (!placementsFit(placements, gridRows, gridCols)) {
            throw new IllegalArgumentException("One or more cars are outside the grid.");
        }
        if (!fixturesFit(fixtures, gridRows, gridCols)) {
            throw new IllegalArgumentException("One or more lifts or gates are outside the grid.");
        }
        Set<String> cells = new HashSet<>();
        if (placements != null) {
            for (GridPlacement p : placements) {
                if (p.slotNumber() < 1 || p.slotNumber() > slotCount) {
                    throw new IllegalArgumentException("Invalid slot number: " + p.slotNumber());
                }
                if (!cells.add(p.col() + ":" + p.row())) {
                    throw new IllegalArgumentException("Two items cannot occupy the same grid cell.");
                }
            }
        }
        if (fixtures != null) {
            Set<String> carLiftKeys = new HashSet<>();
            Set<String> passengerLiftKeys = new HashSet<>();
            Set<String> gateKeys = new HashSet<>();
            for (FixturePlacement f : fixtures) {
                String kind = normalizeFixtureKind(f.kind());
                if (!cells.add(f.col() + ":" + f.row())) {
                    throw new IllegalArgumentException("Two items cannot occupy the same grid cell.");
                }
                String key = kind + ":" + f.index();
                switch (kind) {
                    case "CAR_LIFT" -> {
                        if (!carLiftKeys.add(key)) {
                            throw new IllegalArgumentException(
                                    "Duplicate car lift index: " + f.index());
                        }
                    }
                    case "PASSENGER_LIFT" -> {
                        if (!passengerLiftKeys.add(key)) {
                            throw new IllegalArgumentException(
                                    "Duplicate passenger lift index: " + f.index());
                        }
                    }
                    case "GATE" -> {
                        if (!gateKeys.add(key)) {
                            throw new IllegalArgumentException("Duplicate gate index: " + f.index());
                        }
                    }
                    default ->
                            throw new IllegalArgumentException("Unknown fixture kind: " + f.kind());
                }
            }
        }
    }

    public static String normalizeFixtureKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Fixture kind is required.");
        }
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        if ("LIFT".equals(normalized)) {
            return "CAR_LIFT";
        }
        if ("CAR_LIFT".equals(normalized)
                || "PASSENGER_LIFT".equals(normalized)
                || "GATE".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unknown fixture kind: " + kind);
    }

    public static void clearAll(Building building) {
        building.setParkingFloorConfig(null);
    }

    public record GridRowAdjustResult(
            int gridRows, List<GridPlacement> placements, List<FixturePlacement> fixtures) {}

    public static boolean rowHasAnyItem(
            List<GridPlacement> placements, List<FixturePlacement> fixtures, int row) {
        if (placements != null) {
            for (GridPlacement placement : placements) {
                if (placement.row() == row) {
                    return true;
                }
            }
        }
        if (fixtures != null) {
            for (FixturePlacement fixture : fixtures) {
                if (fixture.row() == row) {
                    return true;
                }
            }
        }
        return false;
    }

    public static GridRowAdjustResult adjustGridRows(
            int slotCount,
            int gridRows,
            List<GridPlacement> placements,
            List<FixturePlacement> fixtures,
            ParkingGridRowDto.Action action) {
        int minRows = minGridRowsForSlotCount(slotCount);
        List<GridPlacement> currentPlacements =
                placements != null ? new ArrayList<>(placements) : new ArrayList<>();
        List<FixturePlacement> currentFixtures =
                fixtures != null ? new ArrayList<>(fixtures) : new ArrayList<>();
        return switch (action) {
            case INSERT_TOP -> {
                if (gridRows >= MAX_GRID_ROWS) {
                    throw new IllegalArgumentException(
                            "Grid can have at most " + MAX_GRID_ROWS + " rows.");
                }
                List<GridPlacement> shiftedPlacements = new ArrayList<>();
                for (GridPlacement placement : currentPlacements) {
                    shiftedPlacements.add(
                            new GridPlacement(
                                    placement.slotNumber(),
                                    placement.col(),
                                    placement.row() + 1,
                                    placement.orientation()));
                }
                List<FixturePlacement> shiftedFixtures = new ArrayList<>();
                for (FixturePlacement fixture : currentFixtures) {
                    shiftedFixtures.add(
                            new FixturePlacement(
                                    fixture.kind(),
                                    fixture.index(),
                                    fixture.col(),
                                    fixture.row() + 1,
                                    fixture.orientation()));
                }
                yield new GridRowAdjustResult(gridRows + 1, shiftedPlacements, shiftedFixtures);
            }
            case INSERT_BOTTOM -> {
                if (gridRows >= MAX_GRID_ROWS) {
                    throw new IllegalArgumentException(
                            "Grid can have at most " + MAX_GRID_ROWS + " rows.");
                }
                yield new GridRowAdjustResult(gridRows + 1, currentPlacements, currentFixtures);
            }
            case REMOVE_TOP -> {
                if (gridRows <= minRows) {
                    throw new IllegalArgumentException(
                            "Grid needs at least "
                                    + minRows
                                    + " rows for "
                                    + slotCount
                                    + " parking slots.");
                }
                if (rowHasAnyItem(currentPlacements, currentFixtures, 0)) {
                    throw new IllegalArgumentException(
                            "Only empty rows can be removed. Row 1 has items.");
                }
                List<GridPlacement> shiftedPlacements = new ArrayList<>();
                for (GridPlacement placement : currentPlacements) {
                    shiftedPlacements.add(
                            new GridPlacement(
                                    placement.slotNumber(),
                                    placement.col(),
                                    placement.row() - 1,
                                    placement.orientation()));
                }
                List<FixturePlacement> shiftedFixtures = new ArrayList<>();
                for (FixturePlacement fixture : currentFixtures) {
                    shiftedFixtures.add(
                            new FixturePlacement(
                                    fixture.kind(),
                                    fixture.index(),
                                    fixture.col(),
                                    fixture.row() - 1,
                                    fixture.orientation()));
                }
                yield new GridRowAdjustResult(gridRows - 1, shiftedPlacements, shiftedFixtures);
            }
            case REMOVE_BOTTOM -> {
                if (gridRows <= minRows) {
                    throw new IllegalArgumentException(
                            "Grid needs at least "
                                    + minRows
                                    + " rows for "
                                    + slotCount
                                    + " parking slots.");
                }
                int lastRow = gridRows - 1;
                if (rowHasAnyItem(currentPlacements, currentFixtures, lastRow)) {
                    throw new IllegalArgumentException(
                            "Only empty rows can be removed. Row "
                                    + gridRows
                                    + " has items.");
                }
                yield new GridRowAdjustResult(gridRows - 1, currentPlacements, currentFixtures);
            }
        };
    }

    public record GridColAdjustResult(
            int gridCols, List<GridPlacement> placements, List<FixturePlacement> fixtures) {}

    public static boolean colHasAnyItem(
            List<GridPlacement> placements, List<FixturePlacement> fixtures, int col) {
        if (placements != null) {
            for (GridPlacement placement : placements) {
                if (placement.col() == col) {
                    return true;
                }
            }
        }
        if (fixtures != null) {
            for (FixturePlacement fixture : fixtures) {
                if (fixture.col() == col) {
                    return true;
                }
            }
        }
        return false;
    }

    public static GridColAdjustResult adjustGridCols(
            int slotCount,
            int gridCols,
            List<GridPlacement> placements,
            List<FixturePlacement> fixtures,
            ParkingGridColDto.Action action) {
        int minCols = minGridColsForSlotCount(slotCount);
        List<GridPlacement> currentPlacements =
                placements != null ? new ArrayList<>(placements) : new ArrayList<>();
        List<FixturePlacement> currentFixtures =
                fixtures != null ? new ArrayList<>(fixtures) : new ArrayList<>();
        return switch (action) {
            case INSERT_LEFT -> {
                if (gridCols >= MAX_GRID_COLS) {
                    throw new IllegalArgumentException(
                            "Grid can have at most " + MAX_GRID_COLS + " columns.");
                }
                List<GridPlacement> shiftedPlacements = new ArrayList<>();
                for (GridPlacement placement : currentPlacements) {
                    shiftedPlacements.add(
                            new GridPlacement(
                                    placement.slotNumber(),
                                    placement.col() + 1,
                                    placement.row(),
                                    placement.orientation()));
                }
                List<FixturePlacement> shiftedFixtures = new ArrayList<>();
                for (FixturePlacement fixture : currentFixtures) {
                    shiftedFixtures.add(
                            new FixturePlacement(
                                    fixture.kind(),
                                    fixture.index(),
                                    fixture.col() + 1,
                                    fixture.row(),
                                    fixture.orientation()));
                }
                yield new GridColAdjustResult(gridCols + 1, shiftedPlacements, shiftedFixtures);
            }
            case INSERT_RIGHT -> {
                if (gridCols >= MAX_GRID_COLS) {
                    throw new IllegalArgumentException(
                            "Grid can have at most " + MAX_GRID_COLS + " columns.");
                }
                yield new GridColAdjustResult(gridCols + 1, currentPlacements, currentFixtures);
            }
            case REMOVE_LEFT -> {
                if (gridCols <= minCols) {
                    throw new IllegalArgumentException(
                            "Grid needs at least "
                                    + minCols
                                    + " columns for "
                                    + slotCount
                                    + " parking slots.");
                }
                if (colHasAnyItem(currentPlacements, currentFixtures, 0)) {
                    throw new IllegalArgumentException(
                            "Only empty columns can be removed. Column 1 has items.");
                }
                List<GridPlacement> shiftedPlacements = new ArrayList<>();
                for (GridPlacement placement : currentPlacements) {
                    shiftedPlacements.add(
                            new GridPlacement(
                                    placement.slotNumber(),
                                    placement.col() - 1,
                                    placement.row(),
                                    placement.orientation()));
                }
                List<FixturePlacement> shiftedFixtures = new ArrayList<>();
                for (FixturePlacement fixture : currentFixtures) {
                    shiftedFixtures.add(
                            new FixturePlacement(
                                    fixture.kind(),
                                    fixture.index(),
                                    fixture.col() - 1,
                                    fixture.row(),
                                    fixture.orientation()));
                }
                yield new GridColAdjustResult(gridCols - 1, shiftedPlacements, shiftedFixtures);
            }
            case REMOVE_RIGHT -> {
                if (gridCols <= minCols) {
                    throw new IllegalArgumentException(
                            "Grid needs at least "
                                    + minCols
                                    + " columns for "
                                    + slotCount
                                    + " parking slots.");
                }
                int lastCol = gridCols - 1;
                if (colHasAnyItem(currentPlacements, currentFixtures, lastCol)) {
                    throw new IllegalArgumentException(
                            "Only empty columns can be removed. Column "
                                    + gridCols
                                    + " has items.");
                }
                yield new GridColAdjustResult(gridCols - 1, currentPlacements, currentFixtures);
            }
        };
    }

    private static boolean placementsFit(
            List<GridPlacement> placements, int gridRows, int gridCols) {
        if (placements == null) {
            return true;
        }
        for (GridPlacement placement : placements) {
            if (placement.row() < 0 || placement.row() >= gridRows) {
                return false;
            }
            if (placement.col() < 0 || placement.col() >= gridCols) {
                return false;
            }
        }
        return true;
    }

    private static boolean fixturesFit(
            List<FixturePlacement> fixtures, int gridRows, int gridCols) {
        if (fixtures == null || fixtures.isEmpty()) {
            return true;
        }
        for (FixturePlacement fixture : fixtures) {
            if (fixture.row() < 0 || fixture.row() >= gridRows) {
                return false;
            }
            if (fixture.col() < 0 || fixture.col() >= gridCols) {
                return false;
            }
        }
        return true;
    }

    private static String toJson(Map<Integer, FloorConfig> map) {
        try {
            Map<String, FloorConfig> keyed = new LinkedHashMap<>();
            for (Map.Entry<Integer, FloorConfig> e : map.entrySet()) {
                keyed.put(String.valueOf(e.getKey()), e.getValue());
            }
            return JSON.writeValueAsString(keyed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize parking floor config", ex);
        }
    }
}
