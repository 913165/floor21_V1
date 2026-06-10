package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Slab;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.SlabRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate-per-sqft slabs are maintained by the Floor21 platform admin for each builder. Builders do not
 * edit these definitions; they only use booking payment schedule for per-buyer instalments.
 */
@Service
@RequiredArgsConstructor
public class SlabService {

    private final SlabRepository slabRepository;
    private final BuildingRepository buildingRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Slab> listAllForPlatformAdmin() {
        return slabRepository.findAllOrderedForAdmin();
    }

    @Transactional(readOnly = true)
    public List<Slab> listFilteredForPlatformAdmin(UUID builderId, UUID buildingId, String search) {
        String q = search != null ? search.trim() : "";
        if (builderId == null && buildingId == null && q.isEmpty()) {
            return slabRepository.findAllOrderedForAdmin();
        }
        return slabRepository.findFilteredForAdmin(builderId, buildingId, q.isEmpty() ? null : q);
    }

    @Transactional(readOnly = true)
    public Slab getForPlatformAdmin(UUID id) {
        return slabRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Slab not found"));
    }

    @Transactional
    public Slab saveForPlatformAdmin(Slab form) {
        if (form.getBuilder() == null || form.getBuilder().getId() == null) {
            throw new IllegalArgumentException("Builder is required");
        }
        UUID targetBuilderId = form.getBuilder().getId();
        var builder = builderRepository.findById(targetBuilderId).orElseThrow(() -> new ResourceNotFoundException("Builder not found"));
        Slab entity;
        if (form.getId() == null) {
            entity = new Slab();
            entity.setCreatedAt(Instant.now());
        } else {
            entity = slabRepository.findById(form.getId()).orElseThrow(() -> new ResourceNotFoundException("Slab not found"));
        }
        entity.setBuilder(builder);
        if (form.getBuilding() != null && form.getBuilding().getId() != null) {
            Building b =
                    buildingRepository
                            .findByIdAndBuilder_Id(form.getBuilding().getId(), targetBuilderId)
                            .orElse(null);
            entity.setBuilding(b);
        } else {
            entity.setBuilding(null);
        }
        entity.setSortOrder(form.getSortOrder());
        entity.setSlabName(form.getSlabName());
        entity.setDescription(form.getDescription());
        entity.setSuggestedPercent(form.getSuggestedPercent());
        entity.setRatePerSqft(form.getRatePerSqft());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        return slabRepository.save(entity);
    }

    @Transactional
    public void deleteForPlatformAdmin(UUID id) {
        Slab slab = slabRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Slab not found"));
        slabRepository.delete(slab);
    }
}
