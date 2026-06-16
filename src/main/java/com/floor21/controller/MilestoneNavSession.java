package com.floor21.controller;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/** Shared session memory for milestone-area picker selections across related menus. */
final class MilestoneNavSession {

    private static final String LAST_PROJECT_ID = "milestoneNav:lastProjectId";
    private static final String LAST_BUILDING_ID = "milestoneNav:lastBuildingId";
    private static final String LAST_BOOKING_ID = "milestoneNav:lastBookingId";

    record PickerSelection(UUID projectId, UUID buildingId, UUID bookingId) {}

    private MilestoneNavSession() {}

    /**
     * Merge request params with session memory. Building/booking are only restored when their
     * parent selection still matches session — so changing project clears stale building/booking.
     */
    static PickerSelection resolve(HttpSession session, UUID projectId, UUID buildingId, UUID bookingId) {
        UUID sessionProjectId = readProjectId(session);
        UUID sessionBuildingId = readBuildingId(session);
        UUID sessionBookingId = readBookingId(session);

        if (projectId == null) {
            projectId = sessionProjectId;
        }
        if (buildingId == null && projectsAlign(projectId, sessionProjectId)) {
            buildingId = sessionBuildingId;
        }
        if (bookingId == null
                && projectsAlign(projectId, sessionProjectId)
                && buildingsAlign(buildingId, sessionBuildingId)) {
            bookingId = sessionBookingId;
        }
        return new PickerSelection(projectId, buildingId, bookingId);
    }

    /** When a booking is known but building is not, keep the pair for session memory and pickers. */
    static PickerSelection withInferredBuilding(PickerSelection selection, UUID buildingInferredFromBooking) {
        if (selection.buildingId() != null
                || selection.bookingId() == null
                || buildingInferredFromBooking == null) {
            return selection;
        }
        return new PickerSelection(selection.projectId(), buildingInferredFromBooking, selection.bookingId());
    }

    private static boolean projectsAlign(UUID current, UUID session) {
        if (current == null) {
            return session == null;
        }
        return current.equals(session);
    }

    private static boolean buildingsAlign(UUID current, UUID session) {
        if (current == null) {
            return session == null;
        }
        return current.equals(session);
    }

    static PickerSelection resolveProjectBuilding(HttpSession session, UUID projectId, UUID buildingId) {
        PickerSelection selection = resolve(session, projectId, buildingId, null);
        return new PickerSelection(selection.projectId(), selection.buildingId(), null);
    }

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
