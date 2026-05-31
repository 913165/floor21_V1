package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.User;
import com.floor21.entity.UserBuildingAssignment;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.UserBuildingAssignmentRepository;
import com.floor21.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffBuildingAccessService {

    public static final String ROLE_BUILDER_ADMIN = "BUILDER_ADMIN";
    public static final String ROLE_EXECUTIVE = "EXECUTIVE";

    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final UserBuildingAssignmentRepository assignmentRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;

    /**
     * {@code null} = unrestricted (all buildings for the builder). Non-null set = only those buildings.
     */
    @Transactional(readOnly = true)
    public Set<UUID> resolveAllowedBuildingIds(UUID staffUserId, UUID builderId) {
        userRepository
                .findById(staffUserId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        String role = userProjectAssignmentService.getRole(staffUserId, builderId);
        if (ROLE_BUILDER_ADMIN.equals(role)) {
            return null;
        }
        List<UUID> assigned = assignmentRepository.findBuildingIdsByUserIdAndBuilderId(staffUserId, builderId);
        if (assigned.isEmpty()) {
            return null;
        }
        return new HashSet<>(assigned);
    }

    @Transactional(readOnly = true)
    public List<String> describeBuildingAccess(UUID staffUserId, UUID builderId) {
        String role = userProjectAssignmentService.getRole(staffUserId, builderId);
        if (ROLE_BUILDER_ADMIN.equals(role)) {
            return List.of("All buildings (admin)");
        }
        List<UserBuildingAssignment> rows =
                assignmentRepository.findByUser_IdAndBuilding_Builder_IdOrderByBuildingName(staffUserId, builderId);
        if (rows.isEmpty()) {
            return List.of("All buildings");
        }
        return rows.stream().map(a -> a.getBuilding().getBuildingName()).toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> assignedBuildingIds(UUID staffUserId, UUID builderId) {
        return assignmentRepository.findBuildingIdsByUserIdAndBuilderId(staffUserId, builderId);
    }

    @Transactional
    public void replaceAssignments(UUID builderId, User user, String role, List<UUID> buildingIds) {
        assignmentRepository.deleteByUser_IdAndBuilding_Builder_Id(user.getId(), builderId);
        if (!ROLE_EXECUTIVE.equals(role)) {
            return;
        }
        if (buildingIds == null || buildingIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new HashSet<>(buildingIds);
        for (UUID buildingId : unique) {
            Building building =
                    buildingRepository
                            .findByIdAndBuilder_Id(buildingId, builderId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Building "
                                                            + buildingId
                                                            + " does not belong to this builder."));
            assignmentRepository.save(new UserBuildingAssignment(user, building));
        }
    }

    @Transactional
    public void clearProjectBuildingAccess(UUID builderId, UUID userId) {
        assignmentRepository.deleteByUser_IdAndBuilding_Builder_Id(userId, builderId);
    }

    public static void validateRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required.");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_BUILDER_ADMIN.equals(normalized) && !ROLE_EXECUTIVE.equals(normalized)) {
            throw new IllegalArgumentException("Role must be Builder admin or Sales / partner (executive).");
        }
    }

    public static String normalizeRole(String role) {
        validateRole(role);
        return role.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<User> listStaffForBuilding(UUID buildingId, UUID builderId) {
        buildingRepository
                .findByIdAndBuilder_Id(buildingId, builderId)
                .orElseThrow(() -> new IllegalArgumentException("Building not found."));
        return userProjectAssignmentService.listForProject(builderId).stream()
                .map(a -> a.getUser())
                .filter(
                        u -> {
                            String role = userProjectAssignmentService.getRole(u.getId(), builderId);
                            if (ROLE_BUILDER_ADMIN.equals(role)) {
                                return true;
                            }
                            List<UUID> assigned =
                                    assignmentRepository.findBuildingIdsByUserIdAndBuilderId(u.getId(), builderId);
                            if (assigned.isEmpty()) {
                                return true;
                            }
                            return assigned.contains(buildingId);
                        })
                .toList();
    }
}
