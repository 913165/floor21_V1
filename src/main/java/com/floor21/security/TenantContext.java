package com.floor21.security;

import java.util.Set;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    /** {@code null} = all buildings for the tenant; non-null = restricted set. */
    private static final ThreadLocal<Set<UUID>> ALLOWED_BUILDINGS = new ThreadLocal<>();

    private TenantContext() {}

    public static void setBuilderId(UUID builderId) {
        CURRENT.set(builderId);
    }

    public static void setAllowedBuildingIds(Set<UUID> buildingIds) {
        if (buildingIds == null) {
            ALLOWED_BUILDINGS.remove();
        } else {
            ALLOWED_BUILDINGS.set(buildingIds);
        }
    }

    public static UUID requireBuilderId() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("Tenant context missing");
        }
        return id;
    }

    public static UUID getBuilderIdOrNull() {
        return CURRENT.get();
    }

    public static boolean hasUnrestrictedBuildingAccess() {
        return ALLOWED_BUILDINGS.get() == null;
    }

    public static boolean canAccessBuilding(UUID buildingId) {
        Set<UUID> allowed = ALLOWED_BUILDINGS.get();
        if (allowed == null) {
            return true;
        }
        return buildingId != null && allowed.contains(buildingId);
    }

    public static Set<UUID> getAllowedBuildingIdsOrNull() {
        Set<UUID> allowed = ALLOWED_BUILDINGS.get();
        return allowed == null ? null : Set.copyOf(allowed);
    }

    public static void clear() {
        CURRENT.remove();
        ALLOWED_BUILDINGS.remove();
    }
}
