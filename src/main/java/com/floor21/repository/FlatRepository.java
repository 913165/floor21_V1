package com.floor21.repository;

import com.floor21.entity.Flat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlatRepository extends JpaRepository<Flat, UUID> {

    List<Flat> findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(UUID buildingId, UUID builderId);

    long countByBuilding_IdAndBuilder_Id(UUID buildingId, UUID builderId);

    Optional<Flat> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query("select f from Flat f join fetch f.building b join fetch b.builder where f.id = :id")
    Optional<Flat> findByIdWithBuilding(@Param("id") UUID id);

    java.util.List<Flat> findByBuilder_IdAndParkingFalseAndStatusInOrderByBuilding_BuildingNameAscFloorNumberAscUnitNumberAsc(
            UUID builderId, java.util.Collection<String> statuses);

    long countByBuilder_Id(UUID builderId);

    long countByBuilder_IdAndStatus(UUID builderId, String status);

    @Query("select count(f) from Flat f where f.status = :status")
    long countAllByStatus(@Param("status") String status);

    @Modifying(clearAutomatically = true)
    @Query("delete from Flat f where f.building.id = :buildingId and f.builder.id = :builderId")
    void deleteByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);
}
