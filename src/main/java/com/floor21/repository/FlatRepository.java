package com.floor21.repository;

import com.floor21.entity.Flat;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlatRepository extends JpaRepository<Flat, UUID> {

    List<Flat> findByBuilding_IdAndBuilder_IdOrderByFloorNumberDescUnitNumberAsc(UUID buildingId, UUID builderId);

    long countByBuilding_IdAndBuilder_Id(UUID buildingId, UUID builderId);

    long countByBuilding_IdAndBuilder_IdAndStatus(
            UUID buildingId, UUID builderId, String status);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId,
            @Param("builderId") UUID builderId,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and f.status = :status
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilding_IdAndBuilder_IdAndStatus(
            @Param("buildingId") UUID buildingId,
            @Param("builderId") UUID builderId,
            @Param("status") String status,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilder_Id(
            @Param("builderId") UUID builderId,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.status = :status
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilder_IdAndStatus(
            @Param("builderId") UUID builderId,
            @Param("status") String status,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id in :buildingIds
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilder_IdAndBuilding_IdIn(
            @Param("builderId") UUID builderId,
            @Param("buildingIds") java.util.Collection<UUID> buildingIds,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id in :buildingIds
              and f.status = :status
              and f.parking = false
              and f.duplexPrimaryFlatId is null
              and f.mergedIntoFlatId is null
              and upper(f.bhkType) not in :nonResidentialTypes
            """)
    long countResidentialByBuilder_IdAndStatusAndBuilding_IdIn(
            @Param("builderId") UUID builderId,
            @Param("status") String status,
            @Param("buildingIds") java.util.Collection<UUID> buildingIds,
            @Param("nonResidentialTypes") java.util.Collection<String> nonResidentialTypes);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and (f.parking = true or upper(f.bhkType) in ('PKG', 'PARKING'))
            """)
    long countParkingByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and (f.parking = true or upper(f.bhkType) in ('PKG', 'PARKING'))
              and f.linkedResidentialFlatId is not null
            """)
    long countLinkedParkingByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and upper(f.bhkType) = 'SHOP'
            """)
    long countShopsByBuilding_IdAndBuilder_Id(
            @Param("buildingId") UUID buildingId, @Param("builderId") UUID builderId);

    @Query(
            """
            select count(f) from Flat f
            where f.builder.id = :builderId
              and f.building.id = :buildingId
              and upper(f.bhkType) = 'SHOP'
              and f.status = :status
            """)
    long countShopsByBuilding_IdAndBuilder_IdAndStatus(
            @Param("buildingId") UUID buildingId,
            @Param("builderId") UUID builderId,
            @Param("status") String status);

    Optional<Flat> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flat f where f.id = :id and f.builder.id = :builderId")
    Optional<Flat> findByIdAndBuilder_IdForUpdate(@Param("id") UUID id, @Param("builderId") UUID builderId);

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
    @Query(
            """
            update Flat f set
              f.duplexPrimaryFlatId = null,
              f.duplexSecondaryFlatId = null,
              f.mergedIntoFlatId = null,
              f.mergedAbsorbedFlatId = null
            where f.building.id = :buildingId
            """)
    void clearUnitLinksForBuilding(@Param("buildingId") UUID buildingId);

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

    Optional<Flat> findByBuilding_IdAndFlatNumber(UUID buildingId, String flatNumber);

    List<Flat> findByBuilding_IdAndBuilder_IdAndFloorNumberBetweenOrderByFloorNumberDescUnitNumberAsc(
            UUID buildingId, UUID builderId, int fromFloor, int toFloor);

    @Query(
            """
            select f from Flat f
            where f.building.id = :buildingId
              and f.builder.id = :builderId
              and f.linkedResidentialFlatId = :residentialFlatId
            order by f.floorNumber asc, f.unitNumber asc
            """)
    List<Flat> findLinkedParkingByResidentialFlatId(
            @Param("buildingId") UUID buildingId,
            @Param("builderId") UUID builderId,
            @Param("residentialFlatId") UUID residentialFlatId);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            delete from Flat f
            where f.building.id = :buildingId
              and f.builder.id = :builderId
              and f.floorNumber between :fromFloor and :toFloor
            """)
    void deleteByBuilding_IdAndBuilder_IdAndFloorNumberBetween(
            @Param("buildingId") UUID buildingId,
            @Param("builderId") UUID builderId,
            @Param("fromFloor") int fromFloor,
            @Param("toFloor") int toFloor);
}
