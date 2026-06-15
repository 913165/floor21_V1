package com.floor21.util;

import com.floor21.entity.Flat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Residential BHK types plus parking and amenity unit labels for flat grid admin edits. */
public final class FlatUnitTypes {

    private static final Set<String> PARKING_CODES = Set.of("PKG", "PARKING");
    private static final Set<String> SHOP_CODES = Set.of("SHOP");

    private static final List<String> AMENITY =
            List.of("GYM", "CLUB", "LOBBY", "TERRACE", "STORAGE", "OFFICE", "MECHANICAL", "REFUGE");

    private FlatUnitTypes() {}

    public static List<String> amenityTypes() {
        return AMENITY;
    }

    /** All unit types shown in platform-admin flat edit (residential + parking + amenities). */
    public static List<String> allForAdminSelect() {
        List<String> all = new ArrayList<>(ResidentialBhkTypes.all());
        all.add("PKG");
        all.add("SHOP");
        all.addAll(AMENITY);
        return all;
    }

    public static Set<String> amenityCodesUpper() {
        return Set.copyOf(AMENITY);
    }

    /** Unit type codes excluded from residential booking dropdowns (amenities + retail shops). */
    public static java.util.List<String> nonBookableUnitTypeCodesUpper() {
        java.util.List<String> excluded = new java.util.ArrayList<>(AMENITY);
        excluded.addAll(SHOP_CODES);
        return excluded;
    }

    public static boolean isParkingCode(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return false;
        }
        return PARKING_CODES.contains(unitType.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isShopCode(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return false;
        }
        return SHOP_CODES.contains(unitType.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isAmenityCode(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return false;
        }
        return AMENITY.contains(unitType.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isDuplexSecondary(Flat flat) {
        return flat != null && flat.getDuplexPrimaryFlatId() != null;
    }

    public static boolean isDuplexPrimary(Flat flat) {
        return flat != null && flat.getDuplexSecondaryFlatId() != null;
    }

    public static boolean isMergeAbsorbed(Flat flat) {
        return flat != null && flat.getMergedIntoFlatId() != null;
    }

    public static boolean isMergePrimary(Flat flat) {
        return flat != null && flat.getMergedAbsorbedFlatId() != null;
    }

    public static boolean isNonBookable(Flat flat) {
        if (flat == null) {
            return true;
        }
        if (Boolean.TRUE.equals(flat.getParking())) {
            return true;
        }
        if (isDuplexSecondary(flat)) {
            return true;
        }
        if (isMergeAbsorbed(flat)) {
            return true;
        }
        return isAmenityCode(flat.getBhkType());
    }

    public static String normalize(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            throw new IllegalArgumentException("Unit type is required.");
        }
        String trimmed = unitType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (isParkingCode(trimmed)) {
            return "PKG";
        }
        if (isShopCode(trimmed)) {
            return "SHOP";
        }
        if (isAmenityCode(trimmed)) {
            return trimmed;
        }
        return ResidentialBhkTypes.normalize(trimmed);
    }

    public static void applyToFlat(
            Flat flat,
            String unitType,
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {
        String normalized = normalize(unitType);
        flat.setBhkType(normalized);
        if (isParkingCode(normalized)) {
            flat.setParking(true);
            flat.setAreaSqft(areaSqft != null ? areaSqft : BigDecimal.valueOf(150));
            flat.setCarpetAreaSqft(null);
            flat.setBalconyAreaSqft(null);
            flat.setBasePrice(basePrice != null ? basePrice : BigDecimal.ZERO);
            if (!"BOOKED".equals(flat.getStatus())) {
                flat.setStatus("AVAILABLE");
            }
            return;
        }
        if (isShopCode(normalized)) {
            flat.setParking(false);
            flat.setAreaSqft(areaSqft != null ? areaSqft : BigDecimal.valueOf(350));
            flat.setCarpetAreaSqft(null);
            flat.setBalconyAreaSqft(null);
            flat.setBasePrice(basePrice != null ? basePrice : BigDecimal.ZERO);
            if (!"BOOKED".equals(flat.getStatus())) {
                flat.setStatus("AVAILABLE");
            }
            return;
        }
        flat.setParking(false);
        if (isAmenityCode(normalized)) {
            flat.setAreaSqft(areaSqft != null ? areaSqft : defaultAmenityArea(normalized));
            flat.setCarpetAreaSqft(null);
            flat.setBalconyAreaSqft(null);
            flat.setBasePrice(basePrice != null ? basePrice : BigDecimal.ZERO);
            if (!"BOOKED".equals(flat.getStatus())) {
                flat.setStatus("AVAILABLE");
            }
            return;
        }
        applyResidentialAreaAndPrice(flat, areaSqft, carpetAreaSqft, balconyAreaSqft, basePrice);
    }

    /**
     * Correct super built-up, carpet, balcony, or price on a booked residential flat without changing unit type
     * or booking status.
     */
    public static void applyBookedFlatAdjustments(
            Flat flat,
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {
        if (flat == null) {
            throw new IllegalArgumentException("Flat not found.");
        }
        if (Boolean.TRUE.equals(flat.getParking()) || isParkingCode(flat.getBhkType())) {
            throw new IllegalArgumentException("Use unit type edit for parking slots.");
        }
        if (isAmenityCode(flat.getBhkType())) {
            throw new IllegalArgumentException("Use unit type edit for amenity units.");
        }
        applyResidentialAreaAndPrice(flat, areaSqft, carpetAreaSqft, balconyAreaSqft, basePrice);
    }

    private static void applyResidentialAreaAndPrice(
            Flat flat,
            BigDecimal areaSqft,
            BigDecimal carpetAreaSqft,
            BigDecimal balconyAreaSqft,
            BigDecimal basePrice) {
        if (areaSqft != null) {
            if (areaSqft.signum() <= 0) {
                throw new IllegalArgumentException("Super built-up area must be greater than zero.");
            }
            flat.setAreaSqft(areaSqft);
        }
        if (carpetAreaSqft != null) {
            if (carpetAreaSqft.signum() <= 0) {
                throw new IllegalArgumentException("Carpet area must be greater than zero.");
            }
            flat.setCarpetAreaSqft(carpetAreaSqft);
        }
        if (balconyAreaSqft != null) {
            if (balconyAreaSqft.signum() < 0) {
                throw new IllegalArgumentException("Balcony area cannot be negative.");
            }
            flat.setBalconyAreaSqft(balconyAreaSqft);
        }
        if (basePrice != null) {
            if (basePrice.signum() < 0) {
                throw new IllegalArgumentException("Price cannot be negative.");
            }
            flat.setBasePrice(basePrice);
        }
    }

    public static String displayLabel(String unitType) {
        if (unitType == null || unitType.isBlank()) {
            return "";
        }
        if (isParkingCode(unitType) || "PKG".equalsIgnoreCase(unitType.trim())) {
            return "PKG";
        }
        if (isShopCode(unitType)) {
            return "SHOP";
        }
        return unitType.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal defaultAmenityArea(String amenityCode) {
        return switch (amenityCode) {
            case "GYM" -> BigDecimal.valueOf(800);
            case "CLUB" -> BigDecimal.valueOf(1200);
            case "LOBBY" -> BigDecimal.valueOf(400);
            case "TERRACE" -> BigDecimal.valueOf(600);
            case "STORAGE" -> BigDecimal.valueOf(100);
            case "OFFICE" -> BigDecimal.valueOf(350);
            case "MECHANICAL" -> BigDecimal.valueOf(250);
            case "REFUGE" -> BigDecimal.valueOf(200);
            default -> BigDecimal.valueOf(300);
        };
    }
}
