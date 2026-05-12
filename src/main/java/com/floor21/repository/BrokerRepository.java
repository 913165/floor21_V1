package com.floor21.repository;

import com.floor21.entity.Broker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerRepository extends JpaRepository<Broker, UUID> {

    List<Broker> findByBuilder_IdAndActiveTrueOrderByFullNameAsc(UUID builderId);

    Optional<Broker> findByIdAndBuilder_Id(UUID id, UUID builderId);
}
