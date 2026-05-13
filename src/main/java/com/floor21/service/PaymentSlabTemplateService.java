package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuilderRepository;
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
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<PaymentSlabTemplate> listForBuilderReference() {
        return paymentSlabTemplateRepository.findByBuilder_IdOrderBySortOrderAscIdAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public List<PaymentSlabTemplate> listForTenant() {
        return listForBuilderReference();
    }

    @Transactional(readOnly = true)
    public PaymentSlabTemplate get(UUID id) {
        UUID builderId = TenantContext.requireBuilderId();
        return paymentSlabTemplateRepository
                .findByIdAndBuilder_Id(id, builderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment milestone not found"));
    }

    @Transactional
    public PaymentSlabTemplate save(PaymentSlabTemplate form) {
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        PaymentSlabTemplate entity;
        if (form.getId() == null) {
            entity = new PaymentSlabTemplate();
            entity.setCreatedAt(Instant.now());
            entity.setBuilder(builder);
        } else {
            entity =
                    paymentSlabTemplateRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Payment milestone not found"));
        }
        entity.setSortOrder(form.getSortOrder() != null ? form.getSortOrder() : 0);
        entity.setMilestoneLabel(form.getMilestoneLabel());
        entity.setSuggestedPercent(form.getSuggestedPercent());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        return paymentSlabTemplateRepository.save(entity);
    }
}
