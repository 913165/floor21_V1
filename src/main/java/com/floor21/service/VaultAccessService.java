package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.repository.UserBuildingVaultAccessRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultAccessService {

    private final UserBuildingVaultAccessRepository vaultAccessRepository;

    @Transactional(readOnly = true)
    public boolean canCurrentUserAccessVault() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return false;
        }
        if (principal.isSuperAdmin()) {
            return false;
        }
        UUID builderId = principal.getBuilderId();
        if (builderId == null) {
            return false;
        }
        UUID staffUserId = principal.getStaffUserId();
        if (staffUserId == null) {
            return vaultAccessRepository.existsByBuilding_Builder_IdAndEnabledTrue(builderId);
        }
        return vaultAccessRepository.existsByUser_IdAndEnabledTrue(staffUserId);
    }

    @Transactional(readOnly = true)
    public boolean canUseBuildingInVault(Building building) {
        if (building == null || building.getBuilder() == null) {
            return false;
        }
        if (!TenantContext.canAccessBuilding(building.getId())) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return false;
        }
        UUID builderId = principal.getBuilderId();
        if (builderId == null || !builderId.equals(building.getBuilder().getId())) {
            return false;
        }
        UUID staffUserId = principal.getStaffUserId();
        if (staffUserId == null) {
            return vaultAccessRepository.existsByBuilding_IdAndEnabledTrue(building.getId());
        }
        return vaultAccessRepository.existsByUser_IdAndBuilding_IdAndEnabledTrue(
                staffUserId, building.getId());
    }
}
