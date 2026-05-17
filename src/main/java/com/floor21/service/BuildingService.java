package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Building> listForTenant() {
        return buildingRepository.findByBuilder_IdOrderByBuildingNameAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public Building getForTenant(UUID id) {
        return buildingRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }

    /**
     * Resolves a building for the signed-in builder tenant, or for platform admin viewing any tenant building
     * (e.g. from the all-buildings list without impersonation).
     */
    @Transactional(readOnly = true)
    public Building resolveForAccess(UUID id) {
        UUID tenantId = TenantContext.getBuilderIdOrNull();
        if (tenantId != null) {
            return buildingRepository
                    .findByIdAndBuilder_Id(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
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
        entity.setBhk1PerFloor(form.getBhk1PerFloor() != null ? form.getBhk1PerFloor() : 0);
        entity.setBhk2PerFloor(form.getBhk2PerFloor() != null ? form.getBhk2PerFloor() : 0);
        entity.setBhk3PerFloor(form.getBhk3PerFloor() != null ? form.getBhk3PerFloor() : 0);
        entity.setAddress(form.getAddress());
        entity.setCity(form.getCity());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        if (preserveFp1 != null || preserveFp2 != null || preserveFp3 != null) {
            entity.setFloorPlan1Bhk(preserveFp1);
            entity.setFloorPlan2Bhk(preserveFp2);
            entity.setFloorPlan3Bhk(preserveFp3);
        }
    }
}
