package com.floor21.security;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setBuilderId(UUID builderId) {
        CURRENT.set(builderId);
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

    public static void clear() {
        CURRENT.remove();
    }
}
