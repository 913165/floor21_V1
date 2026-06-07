package com.floor21.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.floor21.entity.Flat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingFlatTypeDefaultsTest {

    @Test
    void resolvePrefersConfiguredDefaultsOverExistingFlat() {
        Flat template = new Flat();
        template.setBhkType("2BHK");
        template.setAreaSqft(new BigDecimal("800"));
        template.setCarpetAreaSqft(new BigDecimal("600"));
        template.setBasePrice(new BigDecimal("7000000"));

        Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> configured =
                Map.of(
                        "2BHK",
                        new BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry(
                                new BigDecimal("920"),
                                new BigDecimal("710"),
                                new BigDecimal("55"),
                                new BigDecimal("8500000")));

        BuildingFlatTypeDefaults.Defaults defaults =
                BuildingFlatTypeDefaults.resolve(configured, List.of(template), "2BHK");

        assertEquals(new BigDecimal("920"), defaults.areaSqft());
        assertEquals(new BigDecimal("710"), defaults.carpetAreaSqft());
        assertEquals(new BigDecimal("55"), defaults.balconyAreaSqft());
        assertEquals(new BigDecimal("8500000"), defaults.basePrice());
    }

    @Test
    void resolveUsesExistingFlatWhenNotConfigured() {
        Flat template = new Flat();
        template.setBhkType("2BHK");
        template.setAreaSqft(new BigDecimal("920"));
        template.setCarpetAreaSqft(new BigDecimal("710"));
        template.setBalconyAreaSqft(new BigDecimal("55"));
        template.setBasePrice(new BigDecimal("8500000"));

        BuildingFlatTypeDefaults.Defaults defaults =
                BuildingFlatTypeDefaults.resolve(Map.of(), List.of(template), "2BHK");

        assertEquals(new BigDecimal("920"), defaults.areaSqft());
        assertEquals(new BigDecimal("710"), defaults.carpetAreaSqft());
        assertEquals(new BigDecimal("55"), defaults.balconyAreaSqft());
        assertEquals(new BigDecimal("8500000"), defaults.basePrice());
    }

    @Test
    void resolveFallsBackToResidentialDefaultsWhenNoTemplate() {
        BuildingFlatTypeDefaults.Defaults defaults =
                BuildingFlatTypeDefaults.resolve(Map.of(), List.of(), "2BHK");

        assertEquals(
                BigDecimal.valueOf(ResidentialBhkTypes.defaultAreaSqft("2BHK")), defaults.areaSqft());
        assertNull(defaults.carpetAreaSqft());
        assertNull(defaults.balconyAreaSqft());
        assertEquals(
                BigDecimal.valueOf(ResidentialBhkTypes.defaultBasePrice("2BHK")), defaults.basePrice());
    }

    @Test
    void applyConfiguredEntryUpdatesAllProvidedFields() {
        Flat flat = new Flat();
        flat.setBhkType("2BHK");
        flat.setStatus("AVAILABLE");
        flat.setAreaSqft(new BigDecimal("800"));
        flat.setBasePrice(new BigDecimal("7000000"));

        BuildingFlatTypeDefaults.applyConfiguredEntry(
                flat,
                new BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry(
                        new BigDecimal("920"),
                        new BigDecimal("710"),
                        new BigDecimal("55"),
                        new BigDecimal("8500000")));

        assertEquals(new BigDecimal("920"), flat.getAreaSqft());
        assertEquals(new BigDecimal("710"), flat.getCarpetAreaSqft());
        assertEquals(new BigDecimal("55"), flat.getBalconyAreaSqft());
        assertEquals(new BigDecimal("8500000"), flat.getBasePrice());
    }

    @Test
    void shouldPropagateTypeDefaultsSkipsParkingAndLinkedUnits() {
        Flat residential = new Flat();
        residential.setBhkType("2BHK");
        residential.setParking(false);

        Flat parking = new Flat();
        parking.setBhkType("PKG");
        parking.setParking(true);

        assertEquals(true, BuildingFlatTypeDefaults.shouldPropagateTypeDefaults(residential, "2BHK"));
        assertEquals(false, BuildingFlatTypeDefaults.shouldPropagateTypeDefaults(parking, "PKG"));
    }

    @Test
    void resolvePrefersColumnDefaultsOverBhkDefaults() {
        Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> bhkConfigured =
                Map.of(
                        "2BHK",
                        new BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry(
                                new BigDecimal("820"),
                                new BigDecimal("600"),
                                null,
                                new BigDecimal("8000000")));
        Map<String, BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry> columnConfigured =
                Map.of(
                        "A",
                        new BuildingUnitTypeDefaultsUtil.TypeDefaultsEntry(
                                new BigDecimal("920"),
                                new BigDecimal("710"),
                                new BigDecimal("55"),
                                new BigDecimal("8500000")));

        BuildingFlatTypeDefaults.Defaults defaults =
                BuildingFlatTypeDefaults.resolve(
                        bhkConfigured, columnConfigured, List.of(), "2BHK", "A");

        assertEquals(new BigDecimal("920"), defaults.areaSqft());
        assertEquals(new BigDecimal("710"), defaults.carpetAreaSqft());
        assertEquals(new BigDecimal("55"), defaults.balconyAreaSqft());
        assertEquals(new BigDecimal("8500000"), defaults.basePrice());
    }

    @Test
    void shouldPropagateColumnDefaultsMatchesLayoutColumnType() {
        Flat flat = new Flat();
        flat.setBhkType("2BHK");
        flat.setLayoutColumnType("A");
        flat.setParking(false);

        assertEquals(true, BuildingFlatTypeDefaults.shouldPropagateColumnDefaults(flat, "A"));
        assertEquals(false, BuildingFlatTypeDefaults.shouldPropagateColumnDefaults(flat, "B"));
    }

    @Test
    void coalesceForEditKeepsCurrentValueUnlessTypeChanged() {
        assertNull(BuildingFlatTypeDefaults.coalesceForEdit(null, new BigDecimal("100"), false));
        assertEquals(
                new BigDecimal("100"),
                BuildingFlatTypeDefaults.coalesceForEdit(null, new BigDecimal("100"), true));
        assertEquals(
                new BigDecimal("120"),
                BuildingFlatTypeDefaults.coalesceForEdit(new BigDecimal("120"), new BigDecimal("100"), false));
    }
}
