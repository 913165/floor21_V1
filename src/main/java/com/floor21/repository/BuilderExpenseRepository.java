package com.floor21.repository;

import com.floor21.entity.BuilderExpense;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuilderExpenseRepository extends JpaRepository<BuilderExpense, UUID> {

    List<BuilderExpense> findByBuilder_IdOrderByExpenseDateDescCreatedAtDesc(UUID builderId);

    Optional<BuilderExpense> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query(
            "select coalesce(sum(e.amount), 0) from BuilderExpense e where e.builder.id = :builderId")
    BigDecimal sumAmountByBuilderId(@Param("builderId") UUID builderId);
}
