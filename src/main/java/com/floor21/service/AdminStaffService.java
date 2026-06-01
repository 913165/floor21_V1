package com.floor21.service;

import com.floor21.dto.StaffMemberView;
import com.floor21.entity.Builder;
import com.floor21.entity.Building;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserBuildingVaultAccessRepository;
import com.floor21.repository.UserRepository;
import com.floor21.util.UserContactFields;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStaffService {

    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;
    private final StaffBuildingAccessService staffBuildingAccessService;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final UserBuildingVaultAccessRepository vaultAccessRepository;

    @Transactional(readOnly = true)
    public Builder requireTenantBuilder(UUID builderId) {
        return builderRepository
                .findById(builderId)
                .filter(b -> !b.isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("Builder not found."));
    }

    @Transactional(readOnly = true)
    public Building requireTenantBuilding(UUID buildingId) {
        return buildingRepository
                .findByIdWithBuilder(buildingId)
                .filter(b -> b.getBuilder() != null && !b.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("Building not found."));
    }

    @Transactional(readOnly = true)
    public List<StaffMemberView> listStaffViews(UUID builderId) {
        requireTenantBuilder(builderId);
        return userProjectAssignmentService.listForProject(builderId).stream()
                .map(this::toStaffMemberView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffMemberView> listStaffViewsForBuilding(UUID buildingId) {
        Building building = requireTenantBuilding(buildingId);
        UUID builderId = building.getBuilder().getId();
        return staffBuildingAccessService.listStaffForBuilding(buildingId, builderId).stream()
                .map(
                        u ->
                                StaffMemberView.from(
                                        u,
                                        userProjectAssignmentService.getRole(u.getId(), builderId),
                                        staffBuildingAccessService.describeBuildingAccess(u.getId(), builderId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public User getStaff(UUID builderId, UUID userId) {
        if (!userProjectAssignmentService.hasMembership(userId, builderId)) {
            throw new IllegalArgumentException("Staff member not found.");
        }
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
    }

    @Transactional(readOnly = true)
    public List<Building> listBuilderBuildings(UUID builderId) {
        requireTenantBuilder(builderId);
        return buildingRepository.findByBuilder_IdOrderByBuildingNameAsc(builderId);
    }

    @Transactional
    public User save(UUID builderId, User form, String rawPassword, String role, List<UUID> buildingIds) {
        Builder builder = requireTenantBuilder(builderId);
        String normalizedRole = StaffBuildingAccessService.normalizeRole(role);
        User entity;
        boolean created = form.getId() == null;
        String password = requirePassword(rawPassword);
        if (created) {
            if (userRepository.existsByEmailIgnoreCase(form.getEmail())) {
                throw new IllegalArgumentException("Email is already used by another user.");
            }
            entity = new User();
            entity.setBuilder(null);
            entity.setCreatedAt(Instant.now());
            entity.setPasswordHash(passwordEncoder.encode(password));
            entity.setAdminVisiblePassword(password);
        } else {
            entity = getStaff(builderId, form.getId());
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(form.getEmail(), entity.getId())) {
                throw new IllegalArgumentException("Email is already used by another user.");
            }
            if (!password.equals(entity.getAdminVisiblePassword())) {
                entity.setPasswordHash(passwordEncoder.encode(password));
            }
            entity.setAdminVisiblePassword(password);
        }
        entity.setRole(normalizedRole);
        entity.setFullName(form.getFullName().trim());
        entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        UserContactFields.applyFromForm(entity, form);
        User saved = userRepository.save(entity);
        userProjectAssignmentService.saveMembership(builderId, saved, builder, normalizedRole);
        staffBuildingAccessService.replaceAssignments(builderId, saved, normalizedRole, buildingIds);
        auditService.log(
                created ? "STAFF_CREATED" : "STAFF_UPDATED",
                "user",
                saved.getId().toString(),
                builderId,
                saved.getEmail() + " (" + normalizedRole + ")");
        return saved;
    }

    @Transactional
    public User assignToProject(UUID builderId, UUID userId, String role, List<UUID> buildingIds) {
        Builder builder = requireTenantBuilder(builderId);
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (userProjectAssignmentService.hasMembership(userId, builderId)) {
            throw new IllegalArgumentException("This user is already a partner on this project.");
        }
        String normalizedRole = StaffBuildingAccessService.normalizeRole(role);
        userProjectAssignmentService.saveMembership(builderId, user, builder, normalizedRole);
        staffBuildingAccessService.replaceAssignments(builderId, user, normalizedRole, buildingIds);
        auditService.log(
                "USER_ASSIGNED_TO_PROJECT",
                "user",
                user.getId().toString(),
                builderId,
                user.getEmail() + " → " + builder.getCompanyName());
        return user;
    }

    @Transactional
    public void removeFromProject(UUID builderId, UUID userId) {
        requireTenantBuilder(builderId);
        User user = getStaff(builderId, userId);
        staffBuildingAccessService.clearProjectBuildingAccess(builderId, userId);
        vaultAccessRepository.deleteByUser_IdAndBuilding_Builder_Id(userId, builderId);
        userProjectAssignmentService.removeMembership(builderId, userId);
        if (user.getBuilder() != null && user.getBuilder().getId().equals(builderId)) {
            user.setBuilder(null);
            userRepository.save(user);
        }
        auditService.log(
                "USER_REMOVED_FROM_PROJECT",
                "user",
                userId.toString(),
                builderId,
                user.getEmail() + " removed from project");
    }

    private StaffMemberView toStaffMemberView(UserProjectAssignment assignment) {
        User user = assignment.getUser();
        UUID builderId = assignment.getBuilder().getId();
        return StaffMemberView.from(
                user,
                assignment.getRole(),
                staffBuildingAccessService.describeBuildingAccess(user.getId(), builderId));
    }

    private static String requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        String trimmed = rawPassword.trim();
        if (trimmed.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        return trimmed;
    }
}
