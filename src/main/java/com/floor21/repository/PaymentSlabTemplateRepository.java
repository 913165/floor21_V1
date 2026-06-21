package com.floor21.repository;

import com.floor21.entity.PaymentSlabTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentSlabTemplateRepository extends JpaRepository<PaymentSlabTemplate, UUID> {

    List<PaymentSlabTemplate> findByBuilding_IdAndActiveTrueOrderBySortOrderAscIdAsc(UUID buildingId);

    List<PaymentSlabTemplate> findByBuilding_IdOrderBySortOrderAscIdAsc(UUID buildingId);

    Optional<PaymentSlabTemplate> findByIdAndBuilding_Id(UUID id, UUID buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentSlabTemplate t WHERE t.building.id = :buildingId")
    void deleteByBuilding_Id(@Param("buildingId") UUID buildingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentSlabTemplate t WHERE t.builder.id = :builderId")
    void deleteByBuilder_Id(@Param("builderId") UUID builderId);
}
