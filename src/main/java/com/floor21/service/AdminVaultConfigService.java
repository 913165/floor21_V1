package com.floor21.service;

import com.floor21.dto.VaultConfigView;
import com.floor21.dto.VaultConfigView.PickerOption;
import com.floor21.dto.VaultConfigView.VaultGrantRow;
import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserBuildingVaultAccess;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserBuildingVaultAccessRepository;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminVaultConfigService {

    public static final String GRANT_PARAM = "grants";
    private static final String GRANT_SEP = "|";

    private final UserBuildingVaultAccessRepository vaultAccessRepository;
    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public VaultConfigView load() {
        return loadInternal(null);
    }

    @Transactional(readOnly = true)
    public VaultConfigView loadForBuilder(UUID builderId) {
        if (builderId == null) {
            throw new IllegalArgumentException("Project is required.");
        }
        builderRepository.findById(builderId).orElseThrow();
        return loadInternal(builderId);
    }

    private VaultConfigView loadInternal(UUID scopedBuilderId) {
        List<VaultGrantRow> grants = new ArrayList<>();
        List<UserBuildingVaultAccess> rows =
                scopedBuilderId == null
                        ? vaultAccessRepository.findAllForAdminOrderByLabels()
                        : vaultAccessRepository.findAllForAdminByBuilderIdOrderByLabels(scopedBuilderId);
        for (UserBuildingVaultAccess g : rows) {
            User u = g.getUser();
            Building b = g.getBuilding();
            grants.add(
                    new VaultGrantRow(
                            u.getId(),
                            b.getId(),
                            b.getBuilder().getId(),
                            userDisplayLabel(u, b.getBuilder().getId()),
                            b.getBuildingName(),
                            b.getBuilder().getCompanyName(),
                            Boolean.TRUE.equals(g.getEnabled())));
        }
        if (scopedBuilderId != null) {
            return new VaultConfigView(
                    grants,
                    loadProjectPicker(scopedBuilderId),
                    loadUserPicker(scopedBuilderId),
                    loadBuildingPicker(scopedBuilderId));
        }
        return new VaultConfigView(grants, loadProjectPicker(), loadUserPicker(), loadBuildingPicker());
    }

    private List<PickerOption> loadProjectPicker() {
        List<PickerOption> options = new ArrayList<>();
        for (Builder builder : builderRepository.findAllTenantsOrderByCompanyNameAsc()) {
            UUID builderId = builder.getId();
            options.add(new PickerOption(builderId, builder.getCompanyName(), builderId));
        }
        return options;
    }

    private List<PickerOption> loadProjectPicker(UUID builderId) {
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        return List.of(new PickerOption(builder.getId(), builder.getCompanyName(), builder.getId()));
    }

    private List<PickerOption> loadUserPicker() {
        return loadUserPicker(null);
    }

    private List<PickerOption> loadUserPicker(UUID scopedBuilderId) {
        List<PickerOption> options = new ArrayList<>();
        for (UserProjectAssignment assignment :
                userProjectAssignmentRepository.findAllWithUserAndBuilderOrderByLabels()) {
            User user = assignment.getUser();
            if (user.getActive() != null && !user.getActive()) {
                continue;
            }
            UUID builderId = assignment.getBuilder().getId();
            if (scopedBuilderId != null && !scopedBuilderId.equals(builderId)) {
                continue;
            }
            options.add(
                    new PickerOption(user.getId(), userDisplayLabel(user, builderId), builderId));
        }
        return options;
    }

    private String userDisplayLabel(User user, UUID builderId) {
        String roleLabel =
                StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(
                                userProjectAssignmentService.getRole(user.getId(), builderId))
                        ? "Builder admin"
                        : "Partner";
        String projectName =
                userProjectAssignmentService.listMemberships(user.getId()).stream()
                        .filter(m -> m.getBuilder().getId().equals(builderId))
                        .map(m -> m.getBuilder().getCompanyName())
                        .findFirst()
                        .orElse("—");
        return projectName
                + " — "
                + user.getFullName()
                + " ("
                + user.getEmail()
                + ") · "
                + roleLabel;
    }

    private List<PickerOption> loadBuildingPicker() {
        return loadBuildingPicker(null);
    }

    private List<PickerOption> loadBuildingPicker(UUID scopedBuilderId) {
        List<PickerOption> options = new ArrayList<>();
        for (Building building : buildingRepository.findAllForPlatformAdminOrderByBuilderAndName()) {
            UUID builderId = building.getBuilder().getId();
            if (scopedBuilderId != null && !scopedBuilderId.equals(builderId)) {
                continue;
            }
            options.add(
                    new PickerOption(
                            building.getId(),
                            building.getBuilder().getCompanyName() + " — " + building.getBuildingName(),
                            builderId));
        }
        return options;
    }

    @Transactional
    public void save(List<String> grantKeys) {
        Set<String> desired = parseGrantKeys(grantKeys);
        vaultAccessRepository.deleteAll();
        persistGrants(desired, null);
        auditService.log("VAULT_CONFIG_UPDATED", "platform_vault_config", null, null, null);
    }

    @Transactional
    public void saveForBuilder(UUID builderId, List<String> grantKeys) {
        if (builderId == null) {
            throw new IllegalArgumentException("Project is required.");
        }
        builderRepository.findById(builderId).orElseThrow();
        Set<String> desired = parseGrantKeys(grantKeys);
        vaultAccessRepository.deleteByBuilding_Builder_Id(builderId);
        persistGrants(desired, builderId);
        auditService.log("VAULT_CONFIG_UPDATED", "tenant_vault_config", null, builderId, null);
    }

    private static Set<String> parseGrantKeys(List<String> grantKeys) {
        Set<String> desired = new HashSet<>();
        if (grantKeys != null) {
            for (String key : grantKeys) {
                if (key != null && !key.isBlank()) {
                    desired.add(key.trim());
                }
            }
        }
        return desired;
    }

    private void persistGrants(Set<String> desired, UUID scopedBuilderId) {
        for (String key : desired) {
            int sep = key.indexOf(GRANT_SEP);
            if (sep <= 0 || sep >= key.length() - 1) {
                continue;
            }
            UUID userId = UUID.fromString(key.substring(0, sep));
            UUID buildingId = UUID.fromString(key.substring(sep + 1));
            User user = userRepository.findById(userId).orElse(null);
            Building building = buildingRepository.findByIdWithBuilder(buildingId).orElse(null);
            if (user == null || building == null) {
                continue;
            }
            UUID grantBuilderId = building.getBuilder().getId();
            if (scopedBuilderId != null && !scopedBuilderId.equals(grantBuilderId)) {
                continue;
            }
            if (!userProjectAssignmentService.hasMembership(userId, grantBuilderId)) {
                continue;
            }
            vaultAccessRepository.save(new UserBuildingVaultAccess(user, building));
        }
    }
}
