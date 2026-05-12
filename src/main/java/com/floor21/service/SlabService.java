package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Slab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlabService {

    private final SlabRepository slabRepository;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Slab> list() {
        return slabRepository.findByBuilder_IdOrderBySlabNameAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public Slab get(UUID id) {
        return slabRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Slab not found"));
    }

    @Transactional
    public Slab save(Slab form) {
        UUID builderId = TenantContext.requireBuilderId();
        var builder = builderRepository.findById(builderId).orElseThrow();
        Slab entity;
        if (form.getId() == null) {
            entity = new Slab();
            entity.setCreatedAt(Instant.now());
        } else {
            entity =
                    slabRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Slab not found"));
        }
        entity.setBuilder(builder);
        if (form.getBuilding() != null && form.getBuilding().getId() != null) {
            Building b =
                    buildingRepository
                            .findByIdAndBuilder_Id(form.getBuilding().getId(), builderId)
                            .orElse(null);
            entity.setBuilding(b);
        } else {
            entity.setBuilding(null);
        }
        entity.setSlabName(form.getSlabName());
        entity.setDescription(form.getDescription());
        entity.setRatePerSqft(form.getRatePerSqft());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        return slabRepository.save(entity);
    }
}
