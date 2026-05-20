package com.floor21.service;

import com.floor21.dto.PartnerFlatAllocationSummaryDto;
import com.floor21.dto.PartnerFlatPickDto;
import com.floor21.entity.Building;
import com.floor21.entity.Flat;
import com.floor21.entity.PartnerFlatAssignment;
import com.floor21.entity.User;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.PartnerFlatAssignmentRepository;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerFlatAllocationService {

    private final PartnerFlatAssignmentRepository assignmentRepository;
    private final FlatRepository flatRepository;
    private final StaffBuildingAccessService staffBuildingAccessService;
    private final BuildingService buildingService;

    @Transactional(readOnly = true)
    public boolean isAllocationActive(UUID buildingId) {
        return assignmentRepository.existsByBuilding_Id(buildingId);
    }

    @Transactional(readOnly = true)
    public List<User> listPartnersForBuilding(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        // Same people as Admin → Partners for this building (builder admins + sales partners).
        return staffBuildingAccessService.listStaffForBuilding(buildingId, builderId).stream()
                .filter(PartnerFlatAllocationService::isActiveUser)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Treat null {@code is_active} as active (legacy rows). Only explicit false is inactive. */
    private static boolean isActiveUser(User user) {
        return !Boolean.FALSE.equals(user.getActive());
    }

    @Transactional(readOnly = true)
    public List<PartnerFlatPickDto> listResidentialFlatsForAllocation(UUID buildingId) {
        Building building = buildingService.resolveForAccess(buildingId);
        return flatRepository
                .findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                        buildingId, building.getBuilder().getId())
                .stream()
                .filter(f -> !Boolean.TRUE.equals(f.getParking()))
                .map(
                        f ->
                                new PartnerFlatPickDto(
                                        f.getId(), f.getFlatNumber(), f.getFloorNumber(), f.getBhkType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UUID getAssignedPartnerIdForFlat(UUID flatId) {
        return assignmentRepository.findByFlat_Id(flatId).map(a -> a.getUser().getId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> getFlatOwnerByPartnerId(UUID buildingId) {
        Map<UUID, UUID> map = new HashMap<>();
        for (PartnerFlatAssignment row : assignmentRepository.findByBuilding_Id(buildingId)) {
            map.put(row.getFlat().getId(), row.getUser().getId());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getFlatPartnerLabels(UUID buildingId) {
        Map<UUID, String> labels = new HashMap<>();
        for (PartnerFlatAssignment row : assignmentRepository.findByBuilding_Id(buildingId)) {
            labels.put(row.getFlat().getId(), row.getUser().getFullName());
        }
        return labels;
    }

    @Transactional(readOnly = true)
    public List<PartnerFlatAllocationSummaryDto> summarize(UUID buildingId) {
        List<User> partners = listPartnersForBuilding(buildingId);
        Map<UUID, Long> counts =
                assignmentRepository.findByBuilding_Id(buildingId).stream()
                        .collect(Collectors.groupingBy(a -> a.getUser().getId(), Collectors.counting()));
        List<PartnerFlatAllocationSummaryDto> rows = new ArrayList<>();
        for (User partner : partners) {
            rows.add(
                    new PartnerFlatAllocationSummaryDto(
                            partner.getId(),
                            partner.getFullName(),
                            counts.getOrDefault(partner.getId(), 0L).intValue()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public int countUnassignedResidential(UUID buildingId) {
        int total = listResidentialFlatsForAllocation(buildingId).size();
        int assigned = assignmentRepository.findByBuilding_Id(buildingId).size();
        return Math.max(0, total - assigned);
    }

    /**
     * Assigns or clears the sales partner for one residential flat.
     *
     * @return partner display name when assigned, or null when unassigned
     */
    @Transactional
    public String assignPartnerToFlat(UUID flatId, UUID partnerUserId) {
        requirePlatformAdmin();
        Flat flat =
                flatRepository
                        .findById(flatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        UUID buildingId = flat.getBuilding().getId();
        buildingService.resolveForAccess(buildingId);
        if (Boolean.TRUE.equals(flat.getParking())) {
            throw new IllegalArgumentException("Parking units cannot be assigned to a partner.");
        }
        assignmentRepository.findByFlat_Id(flatId).ifPresent(assignmentRepository::delete);
        if (partnerUserId == null) {
            return null;
        }
        List<User> partners = listPartnersForBuilding(buildingId);
        User partner =
                partners.stream()
                        .filter(p -> p.getId().equals(partnerUserId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User is not a partner on this building."));
        Building building = buildingService.resolveForAccess(buildingId);
        assignmentRepository.save(new PartnerFlatAssignment(partner, flat, building));
        return partner.getFullName();
    }

    /**
     * Saves partner ownership per residential flat. Map keys are flat IDs; values are partner user IDs or
     * blank/null for unassigned.
     */
    @Transactional
    public void saveAllocations(UUID buildingId, Map<String, String> flatOwnerParams) {
        requirePlatformAdmin();
        Building building = buildingService.resolveForAccess(buildingId);
        UUID builderId = building.getBuilder().getId();
        List<User> partners = listPartnersForBuilding(buildingId);
        Set<UUID> partnerIds = partners.stream().map(User::getId).collect(Collectors.toSet());

        List<Flat> residential =
                flatRepository
                        .findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(
                                buildingId, builderId)
                        .stream()
                        .filter(f -> !Boolean.TRUE.equals(f.getParking()))
                        .toList();
        Set<UUID> residentialIds = residential.stream().map(Flat::getId).collect(Collectors.toSet());

        assignmentRepository.deleteByBuilding_Id(buildingId);

        if (flatOwnerParams == null || flatOwnerParams.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : flatOwnerParams.entrySet()) {
            if (!entry.getKey().startsWith("flatOwner_")) {
                continue;
            }
            String flatIdRaw = entry.getKey().substring("flatOwner_".length());
            UUID flatId;
            try {
                flatId = UUID.fromString(flatIdRaw);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid flat id: " + flatIdRaw);
            }
            if (!residentialIds.contains(flatId)) {
                throw new IllegalArgumentException("Flat does not belong to this building: " + flatId);
            }
            String partnerRaw = entry.getValue();
            if (partnerRaw == null || partnerRaw.isBlank()) {
                continue;
            }
            UUID partnerId;
            try {
                partnerId = UUID.fromString(partnerRaw.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid partner id for flat " + flatId);
            }
            if (!partnerIds.contains(partnerId)) {
                throw new IllegalArgumentException("User is not a sales partner on this building.");
            }
            Flat flat =
                    residential.stream()
                            .filter(f -> f.getId().equals(flatId))
                            .findFirst()
                            .orElseThrow();
            User partner =
                    partners.stream()
                            .filter(p -> p.getId().equals(partnerId))
                            .findFirst()
                            .orElseThrow();
            assignmentRepository.save(new PartnerFlatAssignment(partner, flat, building));
        }
    }

    /**
     * When partner flat allocation exists for the building, sales partners (executives) may only book or hold flats
     * explicitly assigned to them. Unassigned flats and flats assigned to others stay visible on the grid but are
     * not bookable. Builder admins and platform admin are unrestricted.
     */
    @Transactional(readOnly = true)
    public boolean isBookableByCurrentUser(UUID buildingId, UUID assignedPartnerId) {
        if (!isAllocationActive(buildingId) || canBypassPartnerFlatRestriction()) {
            return true;
        }
        UUID staffUserId = currentStaffUserId();
        if (staffUserId == null) {
            return true;
        }
        return assignedPartnerId != null && assignedPartnerId.equals(staffUserId);
    }

    @Transactional(readOnly = true)
    public void assertCanManageFlat(UUID buildingId, UUID flatId) {
        if (canBypassPartnerFlatRestriction()) {
            return;
        }
        UUID assignedPartnerId =
                assignmentRepository
                        .findByFlat_Id(flatId)
                        .map(a -> a.getUser().getId())
                        .orElse(null);
        if (!isBookableByCurrentUser(buildingId, assignedPartnerId)) {
            throw new IllegalArgumentException("This flat is assigned to another partner.");
        }
    }

    private static boolean canBypassPartnerFlatRestriction() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return true;
        }
        if (principal.isSuperAdmin()) {
            return true;
        }
        if (principal.getStaffUserId() == null) {
            return true;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUILDER_ADMIN".equals(a.getAuthority()));
    }

    private static UUID currentStaffUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return null;
        }
        return principal.getStaffUserId();
    }

    private static void requirePlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)
                || !principal.isSuperAdmin()) {
            throw new IllegalArgumentException("Only Floor21 platform admin can manage partner flat allocation.");
        }
        TenantContext.clear();
    }
}
