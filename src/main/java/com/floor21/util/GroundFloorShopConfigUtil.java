package com.floor21.util;



import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.floor21.entity.Building;

import com.floor21.util.GroundFloorStoredConfig.GroundFloorParkingConfig;

import com.floor21.util.ParkingFloorConfigUtil.FixturePlacement;

import com.floor21.util.ParkingFloorConfigUtil.FloorConfig;

import com.floor21.util.ParkingFloorConfigUtil.GridPlacement;

import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;

import java.util.Locale;

import java.util.Set;



/** Ground-floor retail shop grid config stored as JSON on {@link Building}. */

public final class GroundFloorShopConfigUtil {



    public static final BigDecimal DEFAULT_SHOP_AREA_SQFT = BigDecimal.valueOf(350);

    public static final int DEFAULT_SHOP_SIZE_PERCENT = 140;



    private static final ObjectMapper JSON = new ObjectMapper();



    private GroundFloorShopConfigUtil() {}



    public static GroundFloorStoredConfig readStored(Building building) {

        if (building == null

                || building.getGroundFloorConfig() == null

                || building.getGroundFloorConfig().isBlank()) {

            return GroundFloorStoredConfig.empty();

        }

        try {

            String raw = building.getGroundFloorConfig().trim();

            if (raw.contains("\"shops\"")) {

                return JSON.readValue(raw, GroundFloorStoredConfig.class);

            }

            FloorConfig legacy = JSON.readValue(raw, FloorConfig.class);

            return new GroundFloorStoredConfig(legacy, null);

        } catch (Exception ex) {

            return GroundFloorStoredConfig.empty();

        }

    }



    public static FloorConfig read(Building building) {

        return readStored(building).shops();

    }



    public static boolean isConfigured(Building building) {

        return read(building).configured();

    }



    public static BigDecimal resolveShopAreaSqft(FloorConfig config) {

        if (config != null && config.slotAreaSqft() != null && config.slotAreaSqft().signum() > 0) {

            return config.slotAreaSqft();

        }

        return DEFAULT_SHOP_AREA_SQFT;

    }

    /** Ground floor fills left-to-right, then top-to-bottom (slot 1 at top-left). */
    public static int minGridRowsForGroundFloorSlotCount(int slotCount, int gridCols) {
        if (slotCount <= 0) {
            return 1;
        }
        int cols = Math.max(1, gridCols);
        return (int) Math.ceil((double) slotCount / cols);
    }

    /** Minimum columns needed for ground floor slots given the current row count. */
    public static int minGridColsForGroundFloorSlotCount(int slotCount, int gridRows) {
        if (slotCount <= 0) {
            return 1;
        }
        int rows = Math.max(1, gridRows);
        return (int) Math.ceil((double) slotCount / rows);
    }

    public static int normalizeGroundFloorGridRows(int slotCount, int gridCols, Integer gridRows) {
        int min = minGridRowsForGroundFloorSlotCount(Math.max(slotCount, 1), gridCols);
        int value = gridRows != null ? gridRows : min;
        if (value < min) {
            throw new IllegalArgumentException(
                    "Grid needs at least " + min + " rows for " + slotCount + " slots.");
        }
        if (value > ParkingFloorConfigUtil.MAX_GRID_ROWS) {
            throw new IllegalArgumentException(
                    "Grid can have at most " + ParkingFloorConfigUtil.MAX_GRID_ROWS + " rows.");
        }
        return value;
    }

    public static List<GridPlacement> defaultGroundFloorPlacements(
            int slotCount, int gridCols, int gridRows) {
        List<GridPlacement> out = new ArrayList<>();
        int slot = 1;
        for (int row = 0; row < gridRows && slot <= slotCount; row++) {
            for (int col = 0; col < gridCols && slot <= slotCount; col++) {
                out.add(new GridPlacement(slot, col, row, "vertical"));
                slot++;
            }
        }
        if (slot <= slotCount) {
            throw new IllegalArgumentException(
                    "Not enough grid cells for "
                            + slotCount
                            + " slots. Add rows or columns.");
        }
        return out;
    }

    /** Legacy layouts copied parking defaults (slot 1 on the lower row). */
    public static boolean usesParkingStyleShopLayout(List<GridPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return false;
        }
        int row1 = -1;
        int row2 = -1;
        for (GridPlacement p : placements) {
            if (p.slotNumber() == 1) {
                row1 = p.row();
            } else if (p.slotNumber() == 2) {
                row2 = p.row();
            }
        }
        return row1 >= 0 && row2 >= 0 && row1 > row2;
    }

    /**
     * Rewrites shop (and ground parking) placements to top-down order when still on the old
     * parking-style default. Returns true if the building config was updated.
     */
    public static boolean migrateTopDownLayoutIfNeeded(Building building) {
        GroundFloorStoredConfig stored = readStored(building);
        FloorConfig shops = stored.shops();
        if (!shops.configured() || shops.slotCount() <= 0) {
            return false;
        }
        List<GridPlacement> current = shops.placements();
        if (!usesParkingStyleShopLayout(current)) {
            return false;
        }
        int shopCount = shops.slotCount();
        int gridCols =
                shops.gridCols() != null
                        ? shops.gridCols()
                        : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;
        int combined = shopCount + stored.parkingSlotCount();
        int minRows = minGridRowsForGroundFloorSlotCount(Math.max(combined, 1), gridCols);
        int gridRows =
                shops.gridRows() != null
                        ? Math.max(shops.gridRows(), minRows)
                        : minRows;
        List<GridPlacement> shopPlacements =
                defaultGroundFloorPlacements(shopCount, gridCols, gridRows);
        GroundFloorParkingConfig parking = stored.parking();
        List<GridPlacement> parkingPlacements = List.of();
        if (parking != null && parking.slotCount() > 0) {
            Set<String> used = occupiedCells(shopPlacements);
            parkingPlacements =
                    defaultParkingPlacements(parking.slotCount(), gridCols, gridRows, used);
            parking =
                    new GroundFloorParkingConfig(
                            parking.slotCount(),
                            parking.slotAreaSqft(),
                            parking.carSizePercent(),
                            parkingPlacements);
        }
        FloorConfig updatedShops =
                new FloorConfig(
                        shopCount,
                        true,
                        gridCols,
                        gridRows,
                        shopPlacements,
                        shops.carSizePercent(),
                        shops.liftCount(),
                        shops.carLiftCount(),
                        shops.passengerLiftCount(),
                        shops.gateCount(),
                        shops.fixtures(),
                        shops.showLift(),
                        shops.showGate(),
                        shops.slotAreaSqft(),
                        shops.layoutImagePath());
        writeStored(building, new GroundFloorStoredConfig(updatedShops, parking));
        return true;
    }

    public static int normalizeShopSizePercent(Integer value) {
        if (value == null) {
            return DEFAULT_SHOP_SIZE_PERCENT;
        }
        return Math.max(50, Math.min(200, value));
    }

    public static int resolveShopSizePercent(FloorConfig config) {
        if (config == null) {
            return DEFAULT_SHOP_SIZE_PERCENT;
        }
        return normalizeShopSizePercent(config.carSizePercent());
    }

    public static void markConfigured(

            Building building,

            int shopCount,

            BigDecimal shopAreaSqft,

            Integer shopSizePercent,

            int carLiftCount,

            int passengerLiftCount,

            int gateCount,

            int parkingSlotCount,

            BigDecimal parkingSlotAreaSqft,

            Integer parkingCarSizePercent) {

        GroundFloorStoredConfig existing = readStored(building);

        FloorConfig existingShops = existing.shops();

        BigDecimal normalizedArea =

                shopAreaSqft != null && shopAreaSqft.signum() > 0

                        ? shopAreaSqft.setScale(2, RoundingMode.HALF_UP)

                        : DEFAULT_SHOP_AREA_SQFT;

        int gridCols =

                existingShops.gridCols() != null

                        ? existingShops.gridCols()

                        : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;

        int combinedSlots = shopCount + Math.max(0, parkingSlotCount);

        int minGridRows = minGridRowsForGroundFloorSlotCount(Math.max(combinedSlots, 1), gridCols);

        int gridRows =

                existingShops.gridRows() != null

                        ? normalizeGroundFloorGridRows(combinedSlots, gridCols, existingShops.gridRows())

                        : minGridRows;

        List<GridPlacement> shopPlacements;

        if (existingShops.configured()

                && existingShops.slotCount() == shopCount

                && existingShops.placements() != null

                && !existingShops.placements().isEmpty()

                && !usesParkingStyleShopLayout(existingShops.placements())) {

            shopPlacements = existingShops.placements();

        } else {

            shopPlacements = defaultGroundFloorPlacements(shopCount, gridCols, gridRows);

        }

        int normalizedCarLift = ParkingFloorConfigUtil.clampFixtureCount(carLiftCount);

        int normalizedPassengerLift = ParkingFloorConfigUtil.clampFixtureCount(passengerLiftCount);

        int normalizedGate = ParkingFloorConfigUtil.clampFixtureCount(gateCount);

        List<FixturePlacement> fixtures =

                ParkingFloorConfigUtil.defaultFixtures(

                        normalizedCarLift,

                        normalizedPassengerLift,

                        normalizedGate,

                        gridCols,

                        gridRows,

                        shopPlacements);

        FloorConfig shops =

                new FloorConfig(

                        shopCount,

                        true,

                        gridCols,

                        gridRows,

                        shopPlacements,

                        normalizeShopSizePercent(
                                shopSizePercent != null
                                        ? shopSizePercent
                                        : existingShops.carSizePercent()),

                        null,

                        normalizedCarLift,

                        normalizedPassengerLift,

                        normalizedGate,

                        fixtures,

                        null,

                        null,

                        normalizedArea,

                        null);

        GroundFloorParkingConfig parking = buildParkingConfig(existing, parkingSlotCount, parkingSlotAreaSqft, parkingCarSizePercent, gridCols, gridRows, shopPlacements);

        writeStored(building, new GroundFloorStoredConfig(shops, parking));

        building.setGroundFloorShopCount(shopCount);

        building.setGroundFloorShopAreaSqft(shopCount > 0 ? normalizedArea : null);

    }



    private static GroundFloorParkingConfig buildParkingConfig(

            GroundFloorStoredConfig existing,

            int parkingSlotCount,

            BigDecimal parkingSlotAreaSqft,

            Integer parkingCarSizePercent,

            int gridCols,

            int gridRows,

            List<GridPlacement> shopPlacements) {

        if (parkingSlotCount <= 0) {

            return null;

        }

        BigDecimal parkingArea =

                parkingSlotAreaSqft != null && parkingSlotAreaSqft.signum() > 0

                        ? parkingSlotAreaSqft.setScale(2, RoundingMode.HALF_UP)

                        : ParkingFloorConfigUtil.DEFAULT_SLOT_AREA_SQFT;

        int carSize =

                ParkingFloorConfigUtil.normalizeCarSizePercent(parkingCarSizePercent);

        GroundFloorParkingConfig prior = existing.parking();

        List<GridPlacement> parkingPlacements;

        if (prior != null

                && prior.slotCount() == parkingSlotCount

                && prior.placements() != null

                && !prior.placements().isEmpty()) {

            parkingPlacements = prior.placements();

        } else {

            Set<String> used = occupiedCells(shopPlacements);

            parkingPlacements = defaultParkingPlacements(parkingSlotCount, gridCols, gridRows, used);

        }

        return new GroundFloorParkingConfig(parkingSlotCount, parkingArea, carSize, parkingPlacements);

    }



    private static List<GridPlacement> defaultParkingPlacements(

            int count, int gridCols, int gridRows, Set<String> used) {

        List<GridPlacement> out = new ArrayList<>();

        for (int slot = 1; slot <= count; slot++) {

            int[] cell = firstFreeCell(gridCols, gridRows, used);

            if (cell == null) {

                throw new IllegalArgumentException(

                        "Not enough empty grid cells for "

                                + count

                                + " ground parking slot(s). Add rows/columns or reduce counts.");

            }

            out.add(new GridPlacement(slot, cell[0], cell[1], "vertical"));

            used.add(cell[0] + ":" + cell[1]);

        }

        return out;

    }



    private static int[] firstFreeCell(int gridCols, int gridRows, Set<String> used) {

        for (int row = 0; row < gridRows; row++) {

            for (int col = 0; col < gridCols; col++) {

                String key = col + ":" + row;

                if (!used.contains(key)) {

                    return new int[] {col, row};

                }

            }

        }

        return null;

    }



    public static void saveLayout(

            Building building,

            int gridCols,

            int gridRows,

            List<GridPlacement> shopPlacements,

            List<GridPlacement> parkingPlacements,

            List<FixturePlacement> fixtures) {

        GroundFloorStoredConfig stored = readStored(building);

        FloorConfig shops = stored.shops();

        if (!shops.configured()) {

            throw new IllegalArgumentException("Ground floor shops are not configured yet.");

        }

        int shopCount = shops.slotCount();

        int parkingCount = stored.parkingSlotCount();

        int combined = shopCount + parkingCount;

        int normalizedRows = normalizeGroundFloorGridRows(combined, gridCols, gridRows);

        assertGroundLayoutValid(

                shopPlacements,

                parkingPlacements,

                fixtures,

                normalizedRows,

                gridCols,

                shopCount,

                parkingCount,

                shops.resolvedCarLiftCount(),

                shops.resolvedPassengerLiftCount(),

                shops.resolvedGateCount());

        FloorConfig updatedShops =

                new FloorConfig(

                        shopCount,

                        true,

                        gridCols,

                        normalizedRows,

                        List.copyOf(shopPlacements),

                        shops.carSizePercent(),

                        shops.liftCount(),

                        shops.carLiftCount(),

                        shops.passengerLiftCount(),

                        shops.gateCount(),

                        fixtures != null ? List.copyOf(fixtures) : List.of(),

                        shops.showLift(),

                        shops.showGate(),

                        shops.slotAreaSqft(),

                        shops.layoutImagePath());

        GroundFloorParkingConfig parking = stored.parking();

        if (parking != null && parkingCount > 0) {

            parking =

                    new GroundFloorParkingConfig(

                            parkingCount,

                            parking.slotAreaSqft(),

                            parking.carSizePercent(),

                            List.copyOf(parkingPlacements != null ? parkingPlacements : List.of()));

        }

        writeStored(building, new GroundFloorStoredConfig(updatedShops, parking));

    }



    public static void assertGroundLayoutValid(

            List<GridPlacement> shopPlacements,

            List<GridPlacement> parkingPlacements,

            List<FixturePlacement> fixtures,

            int gridRows,

            int gridCols,

            int shopCount,

            int parkingCount,

            int carLiftCount,

            int passengerLiftCount,

            int gateCount) {

        if (shopPlacements == null || shopPlacements.size() != shopCount) {

            throw new IllegalArgumentException(

                    "Layout must include exactly " + shopCount + " shop slots.");

        }

        if (parkingCount > 0) {

            if (parkingPlacements == null || parkingPlacements.size() != parkingCount) {

                throw new IllegalArgumentException(

                        "Layout must include exactly " + parkingCount + " parking slot(s).");

            }

        }

        ParkingFloorConfigUtil.assertLayoutValid(shopPlacements, fixtures, gridRows, gridCols, shopCount);

        Set<String> cells = occupiedCells(shopPlacements);

        if (fixtures != null) {

            for (FixturePlacement f : fixtures) {

                String key = f.col() + ":" + f.row();

                if (!cells.add(key)) {

                    throw new IllegalArgumentException("Two items cannot occupy the same grid cell.");

                }

            }

        }

        if (parkingPlacements != null) {

            for (GridPlacement p : parkingPlacements) {

                if (p.slotNumber() < 1 || p.slotNumber() > parkingCount) {

                    throw new IllegalArgumentException("Invalid parking slot number: " + p.slotNumber());

                }

                String key = p.col() + ":" + p.row();

                if (!cells.add(key)) {

                    throw new IllegalArgumentException("Two items cannot occupy the same grid cell.");

                }

            }

        }

        int carLiftSeen = 0;

        int passengerLiftSeen = 0;

        int gateSeen = 0;

        if (fixtures != null) {

            for (FixturePlacement f : fixtures) {

                String kind = ParkingFloorConfigUtil.normalizeFixtureKind(f.kind());

                switch (kind) {

                    case "CAR_LIFT" -> carLiftSeen++;

                    case "PASSENGER_LIFT" -> passengerLiftSeen++;

                    case "GATE" -> gateSeen++;

                    default -> {}

                }

            }

        }

        if (carLiftSeen != carLiftCount) {

            throw new IllegalArgumentException(

                    "Layout must include exactly " + carLiftCount + " car lift(s).");

        }

        if (passengerLiftSeen != passengerLiftCount) {

            throw new IllegalArgumentException(

                    "Layout must include exactly " + passengerLiftCount + " passenger lift(s).");

        }

        if (gateSeen != gateCount) {

            throw new IllegalArgumentException(

                    "Layout must include exactly " + gateCount + " gate(s).");

        }

    }



    public static ParkingFloorConfigUtil.GridRowAdjustResult adjustGridRows(

            Building building, com.floor21.dto.ParkingGridRowDto.Action action) {

        GroundFloorStoredConfig stored = readStored(building);

        FloorConfig shops = stored.shops();

        int combined = shops.slotCount() + stored.parkingSlotCount();

        int gridCols =

                shops.gridCols() != null ? shops.gridCols() : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;

        int gridRows =

                shops.gridRows() != null

                        ? shops.gridRows()

                        : minGridRowsForGroundFloorSlotCount(combined, gridCols);

        List<GridPlacement> parkingPlacements =

                stored.parking() != null && stored.parking().placements() != null

                        ? stored.parking().placements()

                        : List.of();

        if (!rowActionAllowed(stored, action, gridRows)) {

            throw new IllegalArgumentException("Cannot remove a row that contains shops, parking, lifts, or gates.");

        }

        int minRows = minGridRowsForGroundFloorSlotCount(combined, gridCols);

        ParkingFloorConfigUtil.GridRowAdjustResult adjusted =

                ParkingFloorConfigUtil.adjustGridRows(

                        minRows,

                        combined,

                        gridRows,

                        shops.placements(),

                        shops.fixtures(),

                        action);

        List<GridPlacement> shiftedParking = shiftParkingPlacements(parkingPlacements, action);

        saveLayout(

                building,

                shops.gridCols() != null ? shops.gridCols() : ParkingFloorConfigUtil.DEFAULT_GRID_COLS,

                adjusted.gridRows(),

                adjusted.placements(),

                shiftedParking,

                adjusted.fixtures());

        return adjusted;

    }



    public static ParkingFloorConfigUtil.GridColAdjustResult adjustGridCols(

            Building building, com.floor21.dto.ParkingGridColDto.Action action) {

        GroundFloorStoredConfig stored = readStored(building);

        FloorConfig shops = stored.shops();

        int combined = shops.slotCount() + stored.parkingSlotCount();

        int gridCols =

                shops.gridCols() != null ? shops.gridCols() : ParkingFloorConfigUtil.DEFAULT_GRID_COLS;

        int gridRows =

                shops.gridRows() != null

                        ? shops.gridRows()

                        : minGridRowsForGroundFloorSlotCount(combined, gridCols);

        List<GridPlacement> parkingPlacements =

                stored.parking() != null && stored.parking().placements() != null

                        ? stored.parking().placements()

                        : List.of();

        if (!colActionAllowed(stored, action, gridCols)) {

            throw new IllegalArgumentException("Cannot remove a column that contains shops, parking, lifts, or gates.");

        }

        int minCols = minGridColsForGroundFloorSlotCount(combined, gridRows);

        ParkingFloorConfigUtil.GridColAdjustResult adjusted =

                ParkingFloorConfigUtil.adjustGridCols(

                        minCols,

                        combined,

                        gridCols,

                        shops.placements(),

                        shops.fixtures(),

                        action);

        List<GridPlacement> shiftedParking = shiftParkingPlacementsCol(parkingPlacements, action);

        saveLayout(

                building,

                adjusted.gridCols(),

                shops.gridRows() != null
                        ? shops.gridRows()
                        : minGridRowsForGroundFloorSlotCount(combined, adjusted.gridCols()),

                adjusted.placements(),

                shiftedParking,

                adjusted.fixtures());

        return adjusted;

    }



    private static boolean rowActionAllowed(

            GroundFloorStoredConfig stored, com.floor21.dto.ParkingGridRowDto.Action action, int gridRows) {

        return switch (action) {

            case INSERT_TOP, INSERT_BOTTOM -> true;

            case REMOVE_TOP -> !rowOccupied(stored, 0);

            case REMOVE_BOTTOM -> !rowOccupied(stored, gridRows - 1);

        };

    }



    private static boolean colActionAllowed(

            GroundFloorStoredConfig stored, com.floor21.dto.ParkingGridColDto.Action action, int gridCols) {

        return switch (action) {

            case INSERT_LEFT, INSERT_RIGHT -> true;

            case REMOVE_LEFT -> !colOccupied(stored, 0);

            case REMOVE_RIGHT -> !colOccupied(stored, gridCols - 1);

        };

    }



    private static boolean rowOccupied(GroundFloorStoredConfig stored, int row) {

        if (ParkingFloorConfigUtil.rowHasAnyItem(

                stored.shops().placements(), stored.shops().fixtures(), row)) {

            return true;

        }

        GroundFloorParkingConfig parking = stored.parking();

        if (parking != null && parking.placements() != null) {

            for (GridPlacement p : parking.placements()) {

                if (p.row() == row) {

                    return true;

                }

            }

        }

        return false;

    }



    private static boolean colOccupied(GroundFloorStoredConfig stored, int col) {

        FloorConfig shops = stored.shops();

        if (shops.placements() != null) {

            for (GridPlacement p : shops.placements()) {

                if (p.col() == col) {

                    return true;

                }

            }

        }

        if (shops.fixtures() != null) {

            for (FixturePlacement f : shops.fixtures()) {

                if (f.col() == col) {

                    return true;

                }

            }

        }

        GroundFloorParkingConfig parking = stored.parking();

        if (parking != null && parking.placements() != null) {

            for (GridPlacement p : parking.placements()) {

                if (p.col() == col) {

                    return true;

                }

            }

        }

        return false;

    }



    private static List<GridPlacement> shiftParkingPlacements(

            List<GridPlacement> placements, com.floor21.dto.ParkingGridRowDto.Action action) {

        if (placements == null || placements.isEmpty()) {

            return List.of();

        }

        List<GridPlacement> out = new ArrayList<>();

        for (GridPlacement p : placements) {

            int row = p.row();

            switch (action) {

                case INSERT_TOP -> row++;

                case REMOVE_TOP -> row--;

                default -> {}

            }

            out.add(new GridPlacement(p.slotNumber(), p.col(), row, p.orientation()));

        }

        return out;

    }



    private static List<GridPlacement> shiftParkingPlacementsCol(

            List<GridPlacement> placements, com.floor21.dto.ParkingGridColDto.Action action) {

        if (placements == null || placements.isEmpty()) {

            return List.of();

        }

        List<GridPlacement> out = new ArrayList<>();

        for (GridPlacement p : placements) {

            int col = p.col();

            switch (action) {

                case INSERT_LEFT -> col++;

                case REMOVE_LEFT -> col--;

                default -> {}

            }

            out.add(new GridPlacement(p.slotNumber(), col, p.row(), p.orientation()));

        }

        return out;

    }



    private static Set<String> occupiedCells(List<GridPlacement> placements) {

        Set<String> cells = new HashSet<>();

        if (placements == null) {

            return cells;

        }

        for (GridPlacement p : placements) {

            cells.add(p.col() + ":" + p.row());

        }

        return cells;

    }



    public static void clear(Building building) {

        building.setGroundFloorConfig(null);

        building.setGroundFloorShopCount(0);

        building.setGroundFloorShopAreaSqft(null);

    }

    public static String layoutImagePath(Building building) {
        String path = read(building).layoutImagePath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return path;
    }

    public static void setLayoutImagePath(Building building, String layoutImagePath) {
        GroundFloorStoredConfig stored = readStored(building);
        FloorConfig shops = stored.shops();
        FloorConfig updated =
                new FloorConfig(
                        shops.slotCount(),
                        shops.configured(),
                        shops.gridCols(),
                        shops.gridRows(),
                        shops.placements(),
                        shops.carSizePercent(),
                        shops.liftCount(),
                        shops.carLiftCount(),
                        shops.passengerLiftCount(),
                        shops.gateCount(),
                        shops.fixtures(),
                        shops.showLift(),
                        shops.showGate(),
                        shops.slotAreaSqft(),
                        layoutImagePath);
        writeStored(building, new GroundFloorStoredConfig(updated, stored.parking()));
    }

    private static void writeStored(Building building, GroundFloorStoredConfig config) {

        try {

            building.setGroundFloorConfig(JSON.writeValueAsString(config));

        } catch (JsonProcessingException ex) {

            throw new IllegalStateException("Could not serialize ground floor config", ex);

        }

    }



    public static String normalizeOrientation(String orientation) {

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

}

