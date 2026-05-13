package com.floor21.repository;

import com.floor21.entity.PaymentSlabTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSlabTemplateRepository extends JpaRepository<PaymentSlabTemplate, UUID> {

    List<PaymentSlabTemplate> findByBuilder_IdAndActiveTrueOrderBySortOrderAscIdAsc(UUID builderId);

    List<PaymentSlabTemplate> findByBuilder_IdOrderBySortOrderAscIdAsc(UUID builderId);

    Optional<PaymentSlabTemplate> findByIdAndBuilder_Id(UUID id, UUID builderId);
}
