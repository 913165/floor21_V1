package com.floor21.repository;

import com.floor21.entity.Building;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BuildingRepository extends JpaRepository<Building, UUID> {

    List<Building> findByBuilder_IdOrderByBuildingNameAsc(UUID builderId);

    Optional<Building> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query(
            "select b from Building b join fetch b.builder br order by lower(br.companyName), lower(b.buildingName)")
    List<Building> findAllForPlatformAdminOrderByBuilderAndName();

    long countByBuilder_Id(UUID builderId);

    long countByBuilder_PlatformAdminFalse();
}
