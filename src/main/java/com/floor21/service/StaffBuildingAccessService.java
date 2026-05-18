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

    /**
     * {@code null} = unrestricted (all buildings for the builder). Non-null set = only those buildings.
     */
    @Transactional(readOnly = true)
    public Set<UUID> resolveAllowedBuildingIds(UUID staffUserId) {
        User user =
                userRepository
                        .findById(staffUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        if (ROLE_BUILDER_ADMIN.equals(user.getRole())) {
            return null;
        }
        List<UUID> assigned = assignmentRepository.findBuildingIdsByUserId(staffUserId);
        if (assigned.isEmpty()) {
            return null;
        }
        return new HashSet<>(assigned);
    }

    @Transactional(readOnly = true)
    public List<String> describeBuildingAccess(UUID staffUserId) {
        User user =
                userRepository
                        .findById(staffUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        if (ROLE_BUILDER_ADMIN.equals(user.getRole())) {
            return List.of("All buildings (admin)");
        }
        List<UserBuildingAssignment> rows = assignmentRepository.findByUser_IdOrderByBuilding_BuildingNameAsc(staffUserId);
        if (rows.isEmpty()) {
            return List.of("All buildings");
        }
        return rows.stream().map(a -> a.getBuilding().getBuildingName()).toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> assignedBuildingIds(UUID staffUserId) {
        return assignmentRepository.findBuildingIdsByUserId(staffUserId);
    }

    @Transactional
    public void replaceAssignments(UUID builderId, User user, String role, List<UUID> buildingIds) {
        assignmentRepository.deleteByUser_Id(user.getId());
        if (!ROLE_EXECUTIVE.equals(role)) {
            return;
        }
        if (buildingIds == null || buildingIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one building for sales staff / partner access.");
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
        List<User> all = userRepository.findByBuilder_IdOrderByFullNameAsc(builderId);
        return all.stream()
                .filter(
                        u -> {
                            if (ROLE_BUILDER_ADMIN.equals(u.getRole())) {
                                return true;
                            }
                            long n = assignmentRepository.countByUser_Id(u.getId());
                            if (n == 0) {
                                return true;
                            }
                            return assignmentRepository.existsByUser_IdAndBuilding_Id(u.getId(), buildingId);
                        })
                .toList();
    }
}
