package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.PaymentSlabTemplateRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentSlabTemplateService {

    private final PaymentSlabTemplateRepository paymentSlabTemplateRepository;
    private final BuildingRepository buildingRepository;

    @Transactional(readOnly = true)
    public List<PaymentSlabTemplate> listForBuilding(UUID buildingId) {
        requireBuildingForTenant(buildingId);
        return paymentSlabTemplateRepository.findByBuilding_IdOrderBySortOrderAscIdAsc(buildingId);
    }

    @Transactional(readOnly = true)
    public List<PaymentSlabTemplate> listActiveForBuilding(UUID buildingId) {
        requireBuildingForTenant(buildingId);
        return paymentSlabTemplateRepository.findByBuilding_IdAndActiveTrueOrderBySortOrderAscIdAsc(buildingId);
    }

    @Transactional(readOnly = true)
    public List<PaymentSlabTemplate> listForBuildingAdmin(UUID buildingId) {
        requireBuilding(buildingId);
        return paymentSlabTemplateRepository.findByBuilding_IdOrderBySortOrderAscIdAsc(buildingId);
    }

    @Transactional(readOnly = true)
    public PaymentSlabTemplate getForBuildingAdmin(UUID id, UUID buildingId) {
        requireBuilding(buildingId);
        return paymentSlabTemplateRepository
                .findByIdAndBuilding_Id(id, buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment milestone not found"));
    }

    @Transactional
    public PaymentSlabTemplate saveForBuildingAdmin(PaymentSlabTemplate form, UUID buildingId) {
        Building building = requireBuilding(buildingId);
        PaymentSlabTemplate entity;
        if (form.getId() == null) {
            entity = new PaymentSlabTemplate();
            entity.setCreatedAt(Instant.now());
            entity.setBuilding(building);
            entity.setBuilder(building.getBuilder());
        } else {
            entity =
                    paymentSlabTemplateRepository
                            .findByIdAndBuilding_Id(form.getId(), buildingId)
                            .orElseThrow(() -> new ResourceNotFoundException("Payment milestone not found"));
        }
        entity.setSortOrder(form.getSortOrder() != null ? form.getSortOrder() : 0);
        entity.setMilestoneLabel(form.getMilestoneLabel());
        entity.setSuggestedPercent(form.getSuggestedPercent());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        return paymentSlabTemplateRepository.save(entity);
    }

    private Building requireBuilding(UUID buildingId) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Building is required");
        }
        return buildingRepository
                .findByIdWithBuilder(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }

    private void requireBuildingForTenant(UUID buildingId) {
        if (buildingId == null) {
            throw new IllegalArgumentException("Building is required");
        }
        UUID builderId = TenantContext.requireBuilderId();
        buildingRepository
                .findByIdAndBuilder_Id(buildingId, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }
}
