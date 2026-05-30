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

    @Query(
            """
            select f from Flat f
            where f.builder.id = :builderId
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :amenityTypes
              and f.status in :statuses
            order by f.building.buildingName asc, f.floorNumber asc, f.unitNumber asc
            """)
    java.util.List<Flat> findBookableResidentialByBuilder_IdAndStatusIn(
            @Param("builderId") UUID builderId,
            @Param("amenityTypes") java.util.Collection<String> amenityTypes,
            @Param("statuses") java.util.Collection<String> statuses);

    long countByBuilder_Id(UUID builderId);

    long countByBuilder_IdAndStatus(UUID builderId, String status);

    @Query(
            "select count(f) from Flat f where f.builder.id = :builderId and f.building.id in :buildingIds")
    long countByBuilder_IdAndBuilding_IdIn(
            @Param("builderId") UUID builderId, @Param("buildingIds") java.util.Collection<UUID> buildingIds);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId and f.status = :status and f.building.id in :buildingIds
            """)
    long countByBuilder_IdAndStatusAndBuilding_IdIn(
            @Param("builderId") UUID builderId,
            @Param("status") String status,
            @Param("buildingIds") java.util.Collection<UUID> buildingIds);

    @Query("select count(f) from Flat f where f.status = :status")
    long countAllByStatus(@Param("status") String status);

    @Modifying(clearAutomatically = true)
    @Query("delete from Flat f where f.building.id = :buildingId and f.builder.id = :builderId")
    void deleteByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);

    @Query(
            """
            select coalesce(max(f.floorNumber), 0) from Flat f
            where f.building.id = :buildingId and f.builder.id = :builderId
            """)
    int findMaxFloorNumberByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);

    List<Flat> findByBuilding_IdAndBuilder_IdAndFloorNumberOrderByUnitNumberAsc(
            UUID buildingId, UUID builderId, int floorNumber);
}
