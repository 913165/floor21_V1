package com.floor21.service;

import com.floor21.entity.Bank;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BankRepository;
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
public class BankService {

    private final BankRepository bankRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Bank> list() {
        return bankRepository.findByBuilder_IdOrderByBankNameAscBranchAscIdAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public Bank get(UUID id) {
        return bankRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
    }

    @Transactional
    public Bank save(Bank form) {
        UUID builderId = TenantContext.requireBuilderId();
        var builder = builderRepository.findById(builderId).orElseThrow();
        Bank entity;
        Instant now = Instant.now();
        if (form.getId() == null) {
            entity = new Bank();
            entity.setCreatedAt(now);
        } else {
            entity =
                    bankRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Bank account not found"));
        }
        entity.setBuilder(builder);
        entity.setBankName(form.getBankName());
        entity.setBranch(form.getBranch());
        entity.setIfscCode(form.getIfscCode());
        entity.setAccountNumber(form.getAccountNumber());
        entity.setAccountHolderName(form.getAccountHolderName());
        entity.setNotes(form.getNotes());
        entity.setActive(resolveActive(form.getActive(), form.getId() == null));
        entity.setUpdatedAt(now);
        return bankRepository.save(entity);
    }

    private static boolean resolveActive(Boolean submitted, boolean isNew) {
        if (Boolean.TRUE.equals(submitted)) {
            return true;
        }
        if (Boolean.FALSE.equals(submitted)) {
            return false;
        }
        return isNew;
    }
}
