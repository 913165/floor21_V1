package com.floor21.security;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** HTTP session state for expenses hub PIN unlock (separate from vault and login). */
public final class ExpensesSession {

    public static final String UNLOCKED_BUILDER_ID = "floor21.expenses.unlockedBuilderId";
    public static final String UNLOCKED_AT = "floor21.expenses.unlockedAt";

    private ExpensesSession() {}

    public static boolean isUnlocked(HttpSession session, UUID builderId, Duration maxAge) {
        if (session == null || builderId == null) {
            return false;
        }
        Object storedBuilder = session.getAttribute(UNLOCKED_BUILDER_ID);
        Object storedAt = session.getAttribute(UNLOCKED_AT);
        if (!(storedBuilder instanceof String builderStr) || !(storedAt instanceof Long unlockedAt)) {
            return false;
        }
        if (!builderId.toString().equals(builderStr)) {
            return false;
        }
        Instant expiry = Instant.ofEpochMilli(unlockedAt).plus(maxAge);
        if (Instant.now().isAfter(expiry)) {
            clear(session);
            return false;
        }
        return true;
    }

    public static void unlock(HttpSession session, UUID builderId) {
        session.setAttribute(UNLOCKED_BUILDER_ID, builderId.toString());
        session.setAttribute(UNLOCKED_AT, Instant.now().toEpochMilli());
    }

    public static void clear(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(UNLOCKED_BUILDER_ID);
        session.removeAttribute(UNLOCKED_AT);
    }
}
