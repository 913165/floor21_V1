package com.floor21.util;

import com.floor21.entity.Flat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Resolves area/price defaults for a unit type from building config, then existing flats. */
public final class BuildingFlatTypeDefaults {

    public record Defaults(
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {}

    private BuildingFlatTypeDefaults() {}

    public static Defaults resolve(
            Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> configured,
            List<Flat> buildingFlats,
            String unitType) {
        return resolve(configured, null, buildingFlats, unitType, null);
    }

    public static Defaults resolve(
            Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> bhkConfigured,
            Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> columnConfigured,
            List<Flat> buildingFlats,
            String unitType,
            Integer columnNumber) {
        String normalized = FlatUnitTypes.normalize(unitType);
        if (FlatUnitTypes.isParkingCode(normalized)
                || FlatUnitTypes.isShopCode(normalized)
                || FlatUnitTypes.isAmenityCode(normalized)) {
            return new Defaults(null, null, null, null);
        }
        String columnKey =
                columnNumber != null && columnNumber >= 1
                        ? LayoutColumnTypes.columnDefaultsKey(columnNumber)
                        : null;
        BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry columnEntry =
                columnKey != null && columnConfigured != null
                        ? columnConfigured.get(columnKey)
                        : null;
        BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry bhkEntry =
                bhkConfigured != null ? bhkConfigured.get(normalized) : null;
        Optional<Flat> template = findTemplate(buildingFlats, normalized);
        BigDecimal area =
                firstNonNull(
                        columnEntry != null ? columnEntry.areaSqft() : null,
                        bhkEntry != null ? bhkEntry.areaSqft() : null,
                        template.map(Flat::getAreaSqft).orElse(null),
                        BigDecimal.valueOf(ResidentialBhkTypes.defaultAreaSqft(normalized)));
        BigDecimal carpet =
                firstNonNull(
                        columnEntry != null ? columnEntry.carpetAreaSqft() : null,
                        bhkEntry != null ? bhkEntry.carpetAreaSqft() : null,
                        template.map(Flat::getCarpetAreaSqft).orElse(null));
        BigDecimal balcony =
                firstNonNull(
                        columnEntry != null ? columnEntry.balconyAreaSqft() : null,
                        bhkEntry != null ? bhkEntry.balconyAreaSqft() : null,
                        template.map(Flat::getBalconyAreaSqft).orElse(null));
        BigDecimal price =
                firstNonNull(
                        columnEntry != null ? columnEntry.basePrice() : null,
                        bhkEntry != null ? bhkEntry.basePrice() : null,
                        template.map(Flat::getBasePrice).orElse(null),
                        BigDecimal.valueOf(ResidentialBhkTypes.defaultBasePrice(normalized)));
        return new Defaults(area, carpet, balcony, price);
    }

    public static BigDecimal coalesceForAdd(BigDecimal provided, BigDecimal fallback) {
        return provided != null ? provided : fallback;
    }

    /** When {@code applyFallback} is false, a blank edit keeps the flat's current value. */
    public static BigDecimal coalesceForEdit(
            BigDecimal provided, BigDecimal fallback, boolean applyFallback) {
        if (provided != null) {
            return provided;
        }
        return applyFallback ? fallback : null;
    }

    /** Applies configured building defaults onto one residential flat (all provided fields). */
    public static void applyConfiguredEntry(
            Flat flat, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry entry) {
        if (flat == null || entry == null) {
            return;
        }
        if ("BOOKED".equals(flat.getStatus())) {
            FlatUnitTypes.applyBookedFlatAdjustments(
                    flat,
                    entry.areaSqft(),
                    entry.carpetAreaSqft(),
                    entry.balconyAreaSqft(),
                    entry.basePrice());
            return;
        }
        FlatUnitTypes.applyToFlat(
                flat,
                flat.getBhkType(),
                entry.areaSqft(),
                entry.carpetAreaSqft(),
                entry.balconyAreaSqft(),
                entry.basePrice());
    }

    public static boolean shouldPropagateColumnDefaults(Flat flat, int columnNumber) {
        if (flat == null || columnNumber < 1) {
            return false;
        }
        if (flat.getUnitNumber() == null || flat.getUnitNumber() != columnNumber) {
            return false;
        }
        if (FlatUnitTypes.isDuplexSecondary(flat) || FlatUnitTypes.isMergeAbsorbed(flat)) {
            return false;
        }
        if (Boolean.TRUE.equals(flat.getParking())) {
            return false;
        }
        return !FlatUnitTypes.isAmenityCode(flat.getBhkType());
    }

    public static boolean shouldPropagateTypeDefaults(Flat flat, String unitType) {
        if (flat == null || unitType == null || unitType.isBlank()) {
            return false;
        }
        if (!unitType.equals(FlatUnitTypes.normalize(flat.getBhkType()))) {
            return false;
        }
        if (FlatUnitTypes.isDuplexSecondary(flat) || FlatUnitTypes.isMergeAbsorbed(flat)) {
            return false;
        }
        if (Boolean.TRUE.equals(flat.getParking())) {
            return false;
        }
        return !FlatUnitTypes.isAmenityCode(flat.getBhkType());
    }

    private static BigDecimal firstNonNull(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Optional<Flat> findTemplate(List<Flat> buildingFlats, String normalizedType) {
        if (buildingFlats == null || buildingFlats.isEmpty()) {
            return Optional.empty();
        }
        return buildingFlats.stream()
                .filter(f -> normalizedType.equals(normalizeBhk(f.getBhkType())))
                .filter(f -> !FlatUnitTypes.isDuplexSecondary(f))
                .filter(f -> !FlatUnitTypes.isMergeAbsorbed(f))
                .filter(f -> !FlatUnitTypes.isNonBookable(f))
                .filter(f -> f.getAreaSqft() != null)
                .findFirst();
    }

    private static String normalizeBhk(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return "";
        }
        return unitType.trim().toUpperCase(Locale.ROOT);
    }
}
