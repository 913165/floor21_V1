package com.floor21.repository;

import com.floor21.entity.PaymentSlabTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSlabTemplateRepository extends JpaRepository<PaymentSlabTemplate, UUID> {

    List<PaymentSlabTemplate> findByBuilding_IdAndActiveTrueOrderBySortOrderAscIdAsc(UUID buildingId);

    List<PaymentSlabTemplate> findByBuilding_IdOrderBySortOrderAscIdAsc(UUID buildingId);

    Optional<PaymentSlabTemplate> findByIdAndBuilding_Id(UUID id, UUID buildingId);

    void deleteByBuilding_Id(UUID buildingId);
}
