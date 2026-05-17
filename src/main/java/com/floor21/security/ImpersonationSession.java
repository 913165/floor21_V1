package com.floor21.security;

/** Session keys used when a platform admin impersonates a builder tenant. */
public final class ImpersonationSession {

    public static final String ACTIVE = "floor21.impersonation.active";
    public static final String ADMIN_EMAIL = "floor21.impersonation.adminEmail";
    public static final String BUILDER_ID = "floor21.impersonation.builderId";
    public static final String BUILDER_NAME = "floor21.impersonation.builderName";

    private ImpersonationSession() {}
}
