package com.floor21.service;

import com.floor21.dto.StaffMemberView;
import com.floor21.entity.Builder;
import com.floor21.entity.Building;
import com.floor21.entity.User;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
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
        return userRepository.findByBuilder_IdOrderByFullNameAsc(builderId).stream()
                .map(
                        u ->
                                StaffMemberView.from(
                                        u, staffBuildingAccessService.describeBuildingAccess(u.getId())))
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
                                        u, staffBuildingAccessService.describeBuildingAccess(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public User getStaff(UUID builderId, UUID userId) {
        return userRepository
                .findById(userId)
                .filter(u -> u.getBuilder().getId().equals(builderId))
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
        if (created) {
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Password is required for new staff.");
            }
            if (userRepository.existsByBuilder_IdAndEmailIgnoreCase(builderId, form.getEmail())) {
                throw new IllegalArgumentException("Email is already used for this builder.");
            }
            entity = new User();
            entity.setBuilder(builder);
            entity.setCreatedAt(Instant.now());
            entity.setPasswordHash(passwordEncoder.encode(rawPassword));
            entity.setAdminVisiblePassword(rawPassword);
        } else {
            entity = getStaff(builderId, form.getId());
            if (userRepository.existsByBuilder_IdAndEmailIgnoreCaseAndIdNot(
                    builderId, form.getEmail(), entity.getId())) {
                throw new IllegalArgumentException("Email is already used for this builder.");
            }
            if (rawPassword != null && !rawPassword.isBlank()) {
                String trimmed = rawPassword.trim();
                if (!trimmed.equals(entity.getAdminVisiblePassword())) {
                    entity.setPasswordHash(passwordEncoder.encode(trimmed));
                }
                entity.setAdminVisiblePassword(trimmed);
            }
        }
        entity.setRole(normalizedRole);
        entity.setFullName(form.getFullName().trim());
        entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        User saved = userRepository.save(entity);
        staffBuildingAccessService.replaceAssignments(builderId, saved, normalizedRole, buildingIds);
        auditService.log(
                created ? "STAFF_CREATED" : "STAFF_UPDATED",
                "user",
                saved.getId().toString(),
                builderId,
                saved.getEmail() + " (" + normalizedRole + ")");
        return saved;
    }
}
