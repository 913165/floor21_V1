package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.PaymentSlabTemplateRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.ResidentialBhkTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;
    private final VaultAccessService vaultAccessService;
    private final BookingRepository bookingRepository;
    private final FlatRepository flatRepository;
    private final PaymentSlabTemplateRepository paymentSlabTemplateRepository;
    private final SlabRepository slabRepository;
    private final BuildingFloorPlanService buildingFloorPlanService;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<Building> listForTenant() {
        List<Building> all =
                buildingRepository.findByBuilder_IdOrderByBuildingNameAsc(TenantContext.requireBuilderId());
        return filterByBuildingAccess(all);
    }

    @Transactional(readOnly = true)
    public List<Building> listForVault() {
        return listForTenant().stream().filter(vaultAccessService::canUseBuildingInVault).toList();
    }

    @Transactional(readOnly = true)
    public Building getForTenant(UUID id) {
        Building building =
                buildingRepository
                        .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
        assertBuildingAccess(building.getId());
        return building;
    }

    private static List<Building> filterByBuildingAccess(List<Building> buildings) {
        if (TenantContext.hasUnrestrictedBuildingAccess()) {
            return buildings;
        }
        return buildings.stream()
                .filter(b -> TenantContext.canAccessBuilding(b.getId()))
                .toList();
    }

    private static void assertBuildingAccess(UUID buildingId) {
        if (!TenantContext.canAccessBuilding(buildingId)) {
            throw new ResourceNotFoundException("Building not found");
        }
    }

    /**
     * Resolves a building for the signed-in builder tenant, or for platform admin viewing any tenant building
     * (e.g. from the all-buildings list without impersonation).
     */
    @Transactional(readOnly = true)
    public Building resolveForAccess(UUID id) {
        UUID tenantId = TenantContext.getBuilderIdOrNull();
        if (tenantId != null) {
            Building building =
                    buildingRepository
                            .findByIdAndBuilder_Id(id, tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
            assertBuildingAccess(building.getId());
            return building;
        }
        return buildingRepository
                .findByIdWithBuilder(id)
                .filter(b -> b.getBuilder() != null && !b.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }

    @Transactional(readOnly = true)
    public List<Building> listAllForPlatformAdmin() {
        return buildingRepository.findAllForPlatformAdminOrderByBuilderAndName();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> countBookingsPerBuilding() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : bookingRepository.countGroupedByBuilding()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public void deleteForPlatformAdmin(UUID buildingId, String adminEmail) {
        Building building =
                buildingRepository
                        .findByIdWithBuilder(buildingId)
                        .filter(b -> b.getBuilder() != null && !b.getBuilder().isPlatformAdmin())
                        .orElseThrow(() -> new IllegalArgumentException("Building not found."));
        long bookingCount = bookingRepository.countByBuildingId(buildingId);
        if (bookingCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete this building because "
                            + bookingCount
                            + " booking(s) are linked to its flats.");
        }
        UUID builderId = building.getBuilder().getId();
        String buildingName = building.getBuildingName();

        paymentSlabTemplateRepository.deleteByBuilding_Id(buildingId);
        slabRepository.deleteByBuilding_Id(buildingId);
        flatRepository.clearUnitLinksForBuilding(buildingId);
        flatRepository.deleteByBuilding_IdAndBuilder_Id(buildingId, builderId);
        buildingFloorPlanService.deleteAllForBuilding(buildingId);
        buildingRepository.delete(building);

        auditService.log(
                "BUILDING_DELETED",
                "building",
                buildingId.toString(),
                builderId,
                buildingName + " deleted by " + adminEmail);
    }

    /** New buildings are created by the Floor21 platform admin only (see {@link #createForBuilder}). */
    @Transactional
    public Building createForBuilder(UUID builderId, Building form) {
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Builder not found"));
        if (builder.isPlatformAdmin()) {
            throw new IllegalArgumentException("Cannot attach buildings to the platform admin account.");
        }
        if (buildingRepository.countByBuilder_Id(builderId) > 0) {
            throw new IllegalArgumentException("This project already has a layout.");
        }
        validateBuildingForm(form);
        Building entity = new Building();
        entity.setCreatedAt(Instant.now());
        applyFormFields(entity, builder, form, null, null, null);
        return buildingRepository.save(entity);
    }

    @Transactional
    public Building save(Building form) {
        if (form.getId() == null) {
            throw new IllegalArgumentException(
                    "New buildings can only be created by the Floor21 platform administrator.");
        }
        validateBuildingForm(form);
        Building entity = resolveForAccess(form.getId());
        Builder builder = entity.getBuilder();
        if (builder == null || builder.isPlatformAdmin()) {
            throw new ResourceNotFoundException("Building not found");
        }
        String preserveFp1 = entity.getFloorPlan1Bhk();
        String preserveFp2 = entity.getFloorPlan2Bhk();
        String preserveFp3 = entity.getFloorPlan3Bhk();
        applyFormFields(entity, builder, form, preserveFp1, preserveFp2, preserveFp3);
        return buildingRepository.save(entity);
    }

    private static void validateBuildingForm(Building form) {
        if (form.getBuildingName() == null || form.getBuildingName().isBlank()) {
            throw new IllegalArgumentException("Building name is required.");
        }
        if (form.getTotalFloors() == null || form.getTotalFloors() < 1) {
            throw new IllegalArgumentException("Total floors must be at least 1.");
        }
        if (form.getFlatsPerFloor() == null || form.getFlatsPerFloor() < 1) {
            throw new IllegalArgumentException("Flats per floor must be at least 1.");
        }
        Map<String, Integer> mix = resolveFormMix(form);
        int mixTotal = ResidentialBhkTypes.sumCounts(mix);
        if (mixTotal > 0 && mixTotal != form.getFlatsPerFloor()) {
            throw new IllegalArgumentException(
                    "Unit counts per floor must add up to flats per floor (currently "
                            + mixTotal
                            + ", expected "
                            + form.getFlatsPerFloor()
                            + ").");
        }
    }

    private static Map<String, Integer> resolveFormMix(Building form) {
        if (form.getBhkPerFloor() != null && !form.getBhkPerFloor().isEmpty()) {
            return ResidentialBhkTypes.normalizeMix(form.getBhkPerFloor());
        }
        return ResidentialBhkTypes.countsFromBuilding(form);
    }

    private static void applyFormFields(
            Building entity,
            Builder builder,
            Building form,
            String preserveFp1,
            String preserveFp2,
            String preserveFp3) {
        entity.setBuilder(builder);
        entity.setBuildingName(form.getBuildingName().trim());
        entity.setTotalFloors(form.getTotalFloors());
        entity.setParkingFloors(form.getParkingFloors() != null ? form.getParkingFloors() : 0);
        entity.setFlatsPerFloor(form.getFlatsPerFloor());
        ResidentialBhkTypes.persistMixOnBuilding(entity, resolveFormMix(form));
        entity.setAddress(form.getAddress());
        entity.setCity(form.getCity());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        if (entity.getVaultEnabled() == null) {
            entity.setVaultEnabled(false);
        }
        if (preserveFp1 != null || preserveFp2 != null || preserveFp3 != null) {
            entity.setFloorPlan1Bhk(preserveFp1);
            entity.setFloorPlan2Bhk(preserveFp2);
            entity.setFloorPlan3Bhk(preserveFp3);
        }
    }
}
