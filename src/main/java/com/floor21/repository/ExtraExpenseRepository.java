package com.floor21.repository;

import com.floor21.entity.ExtraExpense;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtraExpenseRepository extends JpaRepository<ExtraExpense, UUID> {

    List<ExtraExpense> findByBooking_IdAndBuilder_IdOrderByExpenseDateDescCreatedAtDesc(
            UUID bookingId, UUID builderId);

    void deleteByBooking_Id(UUID bookingId);

    void deleteByBuilder_Id(UUID builderId);
}
