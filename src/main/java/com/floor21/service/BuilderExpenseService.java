package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.BuilderExpense;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuilderExpenseRepository;
import com.floor21.repository.BuilderRepository;
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
public class BuilderExpenseService {

    private final BuilderExpenseRepository builderExpenseRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<BuilderExpense> listForTenant() {
        return builderExpenseRepository.findByBuilder_IdOrderByExpenseDateDescCreatedAtDesc(
                TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForTenant() {
        return builderExpenseRepository.sumAmountByBuilderId(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public BuilderExpense getForTenant(UUID id) {
        return builderExpenseRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    public BuilderExpense newDraft() {
        BuilderExpense expense = new BuilderExpense();
        expense.setExpenseDate(LocalDate.now());
        return expense;
    }

    @Transactional
    public BuilderExpense save(BuilderExpense form) {
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder = builderRepository.findById(builderId).orElseThrow();

        String description = normalizeRequired(form.getDescription(), "Description");
        BigDecimal amount = normalizeAmount(form.getAmount());
        LocalDate expenseDate = form.getExpenseDate();
        if (expenseDate == null) {
            throw new IllegalArgumentException("Expense date is required.");
        }

        BuilderExpense entity;
        if (form.getId() != null) {
            entity = getForTenant(form.getId());
        } else {
            entity = new BuilderExpense();
            entity.setBuilder(builder);
            entity.setCreatedAt(Instant.now());
        }

        entity.setExpenseDate(expenseDate);
        entity.setDescription(description);
        entity.setCategory(trimToNull(form.getCategory(), 100));
        entity.setPaidTo(trimToNull(form.getPaidTo(), 200));
        entity.setPaymentMode(trimToNull(form.getPaymentMode(), 50));
        entity.setAmount(amount);
        entity.setNotes(trimToNull(form.getNotes(), 500));

        return builderExpenseRepository.save(entity);
    }

    @Transactional
    public void delete(UUID id) {
        BuilderExpense entity = getForTenant(id);
        builderExpenseRepository.delete(entity);
    }

    private static String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return amount;
    }

    private static String trimToNull(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }
}
