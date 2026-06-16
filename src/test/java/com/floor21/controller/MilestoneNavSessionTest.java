package com.floor21.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MilestoneNavSessionTest {

    @Test
    void resolve_doesNotRestoreBuildingWhenProjectChanged() {
        MockHttpSession session = new MockHttpSession();
        UUID oldProject = UUID.randomUUID();
        UUID newProject = UUID.randomUUID();
        UUID oldBuilding = UUID.randomUUID();
        MilestoneNavSession.remember(session, oldProject, oldBuilding, null);

        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, newProject, null, null);

        assertThat(selection.projectId()).isEqualTo(newProject);
        assertThat(selection.buildingId()).isNull();
        assertThat(selection.bookingId()).isNull();
    }

    @Test
    void resolve_restoresBuildingWhenProjectUnchanged() {
        MockHttpSession session = new MockHttpSession();
        UUID project = UUID.randomUUID();
        UUID building = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        MilestoneNavSession.remember(session, project, building, booking);

        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, project, null, null);

        assertThat(selection.projectId()).isEqualTo(project);
        assertThat(selection.buildingId()).isEqualTo(building);
        assertThat(selection.bookingId()).isEqualTo(booking);
    }

    @Test
    void resolve_restoresBuildingAndBookingForTenantWithoutProject() {
        MockHttpSession session = new MockHttpSession();
        UUID building = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        MilestoneNavSession.remember(session, null, building, booking);

        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, null, null, null);

        assertThat(selection.projectId()).isNull();
        assertThat(selection.buildingId()).isEqualTo(building);
        assertThat(selection.bookingId()).isEqualTo(booking);
    }

    @Test
    void withInferredBuilding_fillsMissingBuilding() {
        UUID project = UUID.randomUUID();
        UUID building = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        MilestoneNavSession.PickerSelection selection =
                new MilestoneNavSession.PickerSelection(project, null, booking);

        MilestoneNavSession.PickerSelection completed =
                MilestoneNavSession.withInferredBuilding(selection, building);

        assertThat(completed.buildingId()).isEqualTo(building);
        assertThat(completed.bookingId()).isEqualTo(booking);
    }

    @Test
    void resolve_doesNotRestoreBookingWhenBuildingChanged() {
        MockHttpSession session = new MockHttpSession();
        UUID project = UUID.randomUUID();
        UUID oldBuilding = UUID.randomUUID();
        UUID newBuilding = UUID.randomUUID();
        UUID oldBooking = UUID.randomUUID();
        MilestoneNavSession.remember(session, project, oldBuilding, oldBooking);

        MilestoneNavSession.PickerSelection selection =
                MilestoneNavSession.resolve(session, project, newBuilding, null);

        assertThat(selection.buildingId()).isEqualTo(newBuilding);
        assertThat(selection.bookingId()).isNull();
    }
}
