package com.floor21.controller;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/** Shared session memory for milestone-area picker selections across related menus. */
final class MilestoneNavSession {

    private static final String LAST_PROJECT_ID = "milestoneNav:lastProjectId";
    private static final String LAST_BUILDING_ID = "milestoneNav:lastBuildingId";
    private static final String LAST_BOOKING_ID = "milestoneNav:lastBookingId";

    private MilestoneNavSession() {}

    static UUID readProjectId(HttpSession session) {
        return readUuid(session, LAST_PROJECT_ID);
    }

    static UUID readBuildingId(HttpSession session) {
        return readUuid(session, LAST_BUILDING_ID);
    }

    static UUID readBookingId(HttpSession session) {
        return readUuid(session, LAST_BOOKING_ID);
    }

    static void remember(HttpSession session, UUID projectId, UUID buildingId, UUID bookingId) {
        writeUuid(session, LAST_PROJECT_ID, projectId);
        writeUuid(session, LAST_BUILDING_ID, buildingId);
        writeUuid(session, LAST_BOOKING_ID, bookingId);
    }

    static void rememberProjectBuilding(HttpSession session, UUID projectId, UUID buildingId) {
        writeUuid(session, LAST_PROJECT_ID, projectId);
        writeUuid(session, LAST_BUILDING_ID, buildingId);
    }

    private static UUID readUuid(HttpSession session, String key) {
        Object raw = session.getAttribute(key);
        if (raw instanceof String value && !value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writeUuid(HttpSession session, String key, UUID value) {
        if (value == null) {
            session.removeAttribute(key);
        } else {
            session.setAttribute(key, value.toString());
        }
    }
}
