package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.exception.UnauthorizedTenantException;
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

    @Transactional
    public Building save(Building form) {
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow(() -> new UnauthorizedTenantException("Invalid tenant"));
        String preserveFp1 = null;
        String preserveFp2 = null;
        String preserveFp3 = null;
        Building entity;
        if (form.getId() == null) {
            entity = new Building();
            entity.setCreatedAt(Instant.now());
        } else {
            entity =
                    buildingRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
            preserveFp1 = entity.getFloorPlan1Bhk();
            preserveFp2 = entity.getFloorPlan2Bhk();
            preserveFp3 = entity.getFloorPlan3Bhk();
        }
        entity.setBuilder(builder);
        entity.setBuildingName(form.getBuildingName());
        entity.setTotalFloors(form.getTotalFloors());
        entity.setParkingFloors(form.getParkingFloors() != null ? form.getParkingFloors() : 0);
        entity.setFlatsPerFloor(form.getFlatsPerFloor());
        entity.setBhk1PerFloor(form.getBhk1PerFloor() != null ? form.getBhk1PerFloor() : 0);
        entity.setBhk2PerFloor(form.getBhk2PerFloor() != null ? form.getBhk2PerFloor() : 0);
        entity.setBhk3PerFloor(form.getBhk3PerFloor() != null ? form.getBhk3PerFloor() : 0);
        entity.setAddress(form.getAddress());
        entity.setCity(form.getCity());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        entity.setFloorPlan1Bhk(preserveFp1);
        entity.setFloorPlan2Bhk(preserveFp2);
        entity.setFloorPlan3Bhk(preserveFp3);
        return buildingRepository.save(entity);
    }
}
