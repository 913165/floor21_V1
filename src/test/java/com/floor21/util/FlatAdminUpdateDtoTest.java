package com.floor21.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.entity.Flat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FlatAdminUpdateDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesLayoutColumnType() throws Exception {
        String json =
                "{\"bhkType\":\"2BHK\",\"areaSqft\":820,\"layoutColumnType\":\"B\"}";
        FlatAdminUpdateDto dto = mapper.readValue(json, FlatAdminUpdateDto.class);
        assertEquals("2BHK", dto.bhkType());
        assertEquals("B", dto.layoutColumnType());
    }

    @Test
    void deserializesBlankLayoutColumnTypeAsEmptyString() throws Exception {
        String json = "{\"bhkType\":\"2BHK\",\"layoutColumnType\":\"\"}";
        FlatAdminUpdateDto dto = mapper.readValue(json, FlatAdminUpdateDto.class);
        assertEquals("", dto.layoutColumnType());
    }
    @Test
    void deserializesCarpetAndBalconyAreas() throws Exception {
        String json =
                "{\"bhkType\":\"2BHK\",\"areaSqft\":820,\"carpetAreaSqft\":650.5,\"balconyAreaSqft\":40,\"basePrice\":8400000}";
        FlatAdminUpdateDto dto = mapper.readValue(json, FlatAdminUpdateDto.class);
        assertEquals("2BHK", dto.bhkType());
        assertEquals(new BigDecimal("820"), dto.areaSqft());
        assertEquals(new BigDecimal("650.5"), dto.carpetAreaSqft());
        assertEquals(new BigDecimal("40"), dto.balconyAreaSqft());
        assertEquals(new BigDecimal("8400000"), dto.basePrice());
    }

    @Test
    void applyToFlatPersistsCarpetAndBalcony() {
        Flat flat = new Flat();
        flat.setBhkType("2BHK");
        flat.setAreaSqft(BigDecimal.valueOf(800));

        FlatUnitTypes.applyToFlat(
                flat,
                "2BHK",
                BigDecimal.valueOf(820),
                BigDecimal.valueOf(650),
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(8400000));

        assertEquals(new BigDecimal("820"), flat.getAreaSqft());
        assertEquals(new BigDecimal("650"), flat.getCarpetAreaSqft());
        assertEquals(new BigDecimal("40"), flat.getBalconyAreaSqft());
        assertEquals(new BigDecimal("8400000"), flat.getBasePrice());
    }

    @Test
    void applyBookedFlatAdjustmentsUpdatesAreasAndPriceOnly() {
        Flat flat = new Flat();
        flat.setBhkType("2BHK");
        flat.setAreaSqft(BigDecimal.valueOf(800));
        flat.setStatus("BOOKED");

        FlatUnitTypes.applyBookedFlatAdjustments(
                flat,
                BigDecimal.valueOf(820),
                BigDecimal.valueOf(650),
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(8500000));

        assertEquals("2BHK", flat.getBhkType());
        assertEquals("BOOKED", flat.getStatus());
        assertEquals(new BigDecimal("820"), flat.getAreaSqft());
        assertEquals(new BigDecimal("650"), flat.getCarpetAreaSqft());
        assertEquals(new BigDecimal("40"), flat.getBalconyAreaSqft());
        assertEquals(new BigDecimal("8500000"), flat.getBasePrice());
    }
}
