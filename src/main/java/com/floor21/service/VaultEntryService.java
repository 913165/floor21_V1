package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.VaultEntry;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.VaultEntryRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultEntryService {

    private final VaultEntryRepository vaultEntryRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<VaultEntry> list() {
        return vaultEntryRepository.findByBuilder_IdOrderByEntryDateDescCreatedAtDesc(
                TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAmount() {
        return vaultEntryRepository.sumAmountByBuilderId(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public VaultEntry get(UUID id) {
        return vaultEntryRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Vault entry not found"));
    }

    @Transactional
    public VaultEntry save(VaultEntry form) {
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        Instant now = Instant.now();

        VaultEntry entity;
        if (form.getId() == null) {
            entity = new VaultEntry();
            entity.setCreatedAt(now);
        } else {
            entity =
                    vaultEntryRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Vault entry not found"));
        }

        String clientName = trimRequired(form.getClientName(), "Client name is required.");
        if (form.getAmount() == null || form.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        LocalDate entryDate = form.getEntryDate();
        if (entryDate == null) {
            throw new IllegalArgumentException("Date is required.");
        }

        entity.setBuilder(builder);
        entity.setClientName(clientName);
        entity.setAmount(form.getAmount());
        entity.setEntryDate(entryDate);
        entity.setNotes(trimToNull(form.getNotes()));
        entity.setUpdatedAt(now);
        return vaultEntryRepository.save(entity);
    }

    @Transactional
    public void delete(UUID id) {
        VaultEntry entity = get(id);
        vaultEntryRepository.delete(entity);
    }

    private static String trimRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
