package com.floor21.service;

import com.floor21.entity.Broker;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BrokerRepository;
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
public class BrokerService {

    private final BrokerRepository brokerRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Broker> list() {
        return brokerRepository.findByBuilder_IdAndActiveTrueOrderByFullNameAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public Broker get(UUID id) {
        return brokerRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Broker not found"));
    }

    @Transactional
    public Broker save(Broker form) {
        UUID builderId = TenantContext.requireBuilderId();
        var builder = builderRepository.findById(builderId).orElseThrow();
        Broker entity;
        if (form.getId() == null) {
            entity = new Broker();
            entity.setCreatedAt(Instant.now());
        } else {
            entity =
                    brokerRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Broker not found"));
        }
        entity.setBuilder(builder);
        entity.setFullName(form.getFullName());
        entity.setPhone(form.getPhone());
        entity.setEmail(form.getEmail());
        entity.setCommissionPct(form.getCommissionPct());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        return brokerRepository.save(entity);
    }
}
