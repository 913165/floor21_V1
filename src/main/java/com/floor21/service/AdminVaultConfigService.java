package com.floor21.service;

import com.floor21.dto.VaultConfigView;
import com.floor21.dto.VaultConfigView.PickerOption;
import com.floor21.dto.VaultConfigView.VaultGrantRow;
import com.floor21.entity.Building;
import com.floor21.entity.User;
import com.floor21.entity.UserBuildingVaultAccess;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BuildingRepository;
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
    private final BuildingRepository buildingRepository;
    private final UserProjectAssignmentRepository userProjectAssignmentRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public VaultConfigView load() {
        List<VaultGrantRow> grants = new ArrayList<>();
        for (UserBuildingVaultAccess g : vaultAccessRepository.findAllForAdminOrderByLabels()) {
            User u = g.getUser();
            Building b = g.getBuilding();
            grants.add(
                    new VaultGrantRow(
                            u.getId(),
                            b.getId(),
                            userDisplayLabel(u, b.getBuilder().getId()),
                            b.getBuildingName(),
                            b.getBuilder().getCompanyName(),
                            Boolean.TRUE.equals(g.getEnabled())));
        }
        return new VaultConfigView(grants, loadUserPicker(), loadBuildingPicker());
    }

    private List<PickerOption> loadUserPicker() {
        List<PickerOption> options = new ArrayList<>();
        for (UserProjectAssignment assignment :
                userProjectAssignmentRepository.findAllWithUserAndBuilderOrderByLabels()) {
            User user = assignment.getUser();
            if (user.getActive() != null && !user.getActive()) {
                continue;
            }
            UUID builderId = assignment.getBuilder().getId();
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
        List<PickerOption> options = new ArrayList<>();
        for (Building building : buildingRepository.findAllForPlatformAdminOrderByBuilderAndName()) {
            options.add(
                    new PickerOption(
                            building.getId(),
                            building.getBuilder().getCompanyName() + " — " + building.getBuildingName(),
                            building.getBuilder().getId()));
        }
        return options;
    }

    @Transactional
    public void save(List<String> grantKeys) {
        Set<String> desired = new HashSet<>();
        if (grantKeys != null) {
            for (String key : grantKeys) {
                if (key != null && !key.isBlank()) {
                    desired.add(key.trim());
                }
            }
        }

        vaultAccessRepository.deleteAll();

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
            if (!userProjectAssignmentService.hasMembership(userId, building.getBuilder().getId())) {
                continue;
            }
            vaultAccessRepository.save(new UserBuildingVaultAccess(user, building));
        }

        auditService.log("VAULT_CONFIG_UPDATED", "platform_vault_config", null, null, null);
    }
}
