package com.floor21.util;

import com.floor21.entity.Flat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FlatAdminResponseMaps {

    private FlatAdminResponseMaps() {}

    public static Map<String, Object> fromFlat(Flat flat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", flat.getId());
        map.put("flatNumber", flat.getFlatNumber());
        map.put("bhkType", flat.getBhkType());
        map.put("layoutColumnType", flat.getLayoutColumnType());
        map.put("unitNumber", flat.getUnitNumber());
        map.put(
                "gridTypeLabel",
                LayoutColumnTypes.formatGridTypeLabel(flat.getBhkType(), flat.getLayoutColumnType()));
        map.put("areaSqft", flat.getAreaSqft());
        map.put("carpetAreaSqft", flat.getCarpetAreaSqft());
        map.put("balconyAreaSqft", flat.getBalconyAreaSqft());
        map.put("basePrice", flat.getBasePrice());
        map.put("status", flat.getStatus());
        map.put("floorNumber", flat.getFloorNumber());
        map.put("parking", Boolean.TRUE.equals(flat.getParking()));
        map.put("amenity", FlatUnitTypes.isAmenityCode(flat.getBhkType()));
        map.put("duplexPrimary", FlatUnitTypes.isDuplexPrimary(flat));
        map.put("duplexSecondary", FlatUnitTypes.isDuplexSecondary(flat));
        map.put(
                "duplexPartnerFlatId",
                FlatUnitTypes.isDuplexPrimary(flat)
                        ? flat.getDuplexSecondaryFlatId()
                        : flat.getDuplexPrimaryFlatId());
        map.put("mergePrimary", FlatUnitTypes.isMergePrimary(flat));
        map.put("mergeSecondary", FlatUnitTypes.isMergeAbsorbed(flat));
        map.put(
                "mergePartnerFlatId",
                flat.getMergedAbsorbedFlatId() != null
                        ? flat.getMergedAbsorbedFlatId()
                        : flat.getMergedIntoFlatId());
        map.put("mergeAbsorbedFlatId", flat.getMergedAbsorbedFlatId());
        map.put(
                "hasLayoutImage",
                flat.getLayoutImagePath() != null && !flat.getLayoutImagePath().isBlank());
        return map;
    }
}
