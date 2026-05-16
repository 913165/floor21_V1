package com.floor21.repository;

import com.floor21.entity.VaultEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VaultEntryRepository extends JpaRepository<VaultEntry, UUID> {

    List<VaultEntry> findByBuilder_IdOrderByEntryDateDescCreatedAtDesc(UUID builderId);

    Optional<VaultEntry> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query("select coalesce(sum(v.amount), 0) from VaultEntry v where v.builder.id = :builderId")
    BigDecimal sumAmountByBuilderId(@Param("builderId") UUID builderId);
}
