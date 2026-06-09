package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserBuildingAssignmentRepository;
import com.floor21.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffBuildingAccessServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private BuilderRepository builderRepository;
    @Mock private UserBuildingAssignmentRepository assignmentRepository;
    @Mock private UserProjectAssignmentService userProjectAssignmentService;

    private StaffBuildingAccessService service;

    @BeforeEach
    void setUp() {
        service =
                new StaffBuildingAccessService(
                        userRepository,
                        buildingRepository,
                        builderRepository,
                        assignmentRepository,
                        userProjectAssignmentService);
    }

    @Test
    void resolveAllowedBuildingIds_partnerWithNoAssignments_returnsEmptySet() {
        UUID userId = UUID.randomUUID();
        UUID builderId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(userProjectAssignmentService.getRole(userId, builderId))
                .thenReturn(StaffBuildingAccessService.ROLE_EXECUTIVE);
        when(assignmentRepository.findBuildingIdsByUserIdAndBuilderId(userId, builderId))
                .thenReturn(List.of());

        Set<UUID> allowed = service.resolveAllowedBuildingIds(userId, builderId);

        assertThat(allowed).isEmpty();
    }

    @Test
    void describeBuildingAccess_owner_showsFullAccessWithProjectName() {
        UUID userId = UUID.randomUUID();
        UUID builderId = UUID.randomUUID();
        Builder builder = new Builder();
        builder.setId(builderId);
        builder.setCompanyName("La");
        when(userProjectAssignmentService.getRole(userId, builderId))
                .thenReturn(StaffBuildingAccessService.ROLE_BUILDER_ADMIN);
        when(builderRepository.findById(builderId)).thenReturn(Optional.of(builder));

        assertThat(service.describeBuildingAccess(userId, builderId))
                .containsExactly("Full access (La)");
    }

    @Test
    void describeBuildingAccess_partnerWithNoAssignments_showsNoBuildings() {
        UUID userId = UUID.randomUUID();
        UUID builderId = UUID.randomUUID();
        when(userProjectAssignmentService.getRole(userId, builderId))
                .thenReturn(StaffBuildingAccessService.ROLE_EXECUTIVE);
        when(assignmentRepository.findByUser_IdAndBuilding_Builder_IdOrderByBuildingName(userId, builderId))
                .thenReturn(List.of());

        assertThat(service.describeBuildingAccess(userId, builderId))
                .containsExactly("No buildings assigned");
    }
}
