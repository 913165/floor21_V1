package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.ExtraExpense;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ExtraExpenseRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtraExpenseService {

    private final ExtraExpenseRepository extraExpenseRepository;
    private final BookingRepository bookingRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<ExtraExpense> list(UUID bookingId) {
        return extraExpenseRepository.findByBooking_IdAndBuilder_IdOrderByExpenseDateDescCreatedAtDesc(
                bookingId, TenantContext.requireBuilderId());
    }

    @Transactional
    public ExtraExpense save(UUID bookingId, ExtraExpense form) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_Id(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var builder = builderRepository.findById(builderId).orElseThrow();
        ExtraExpense e = new ExtraExpense();
        e.setBuilder(builder);
        e.setBooking(booking);
        e.setDescription(form.getDescription());
        e.setAmount(form.getAmount());
        e.setExpenseDate(form.getExpenseDate());
        e.setCreatedAt(Instant.now());
        return extraExpenseRepository.save(e);
    }
}
