package com.floor21.repository;

import com.floor21.entity.Booking;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.builder.id = :builderId order by b.bookingDate desc, b.createdAt desc")
    List<Booking> findByBuilder_IdForListUi(@Param("builderId") UUID builderId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.builder.id = :builderId and b.executive.id = :executiveId "
                    + "order by b.bookingDate desc, b.createdAt desc")
    List<Booking> findByBuilder_IdAndExecutive_IdForListUi(
            @Param("builderId") UUID builderId, @Param("executiveId") UUID executiveId);

    Optional<Booking> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.builder.id = :builderId")
    Optional<Booking> findByIdAndBuilder_IdForUpdate(@Param("id") UUID id, @Param("builderId") UUID builderId);

    @Query("select count(b) from Booking b where b.builder.id = :builderId and b.status = 'ACTIVE'")
    long countActiveByBuilder(@Param("builderId") UUID builderId);

    @Query(
            "select count(b) from Booking b where b.builder.id = :builderId and b.flat.building.id = :buildingId "
                    + "and b.status = 'ACTIVE'")
    long countActiveByBuilding(
            @Param("builderId") UUID builderId, @Param("buildingId") UUID buildingId);

    @Query("select count(b) from Booking b where b.flat.building.id = :buildingId")
    long countByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("select b.flat.building.id, count(b) from Booking b group by b.flat.building.id")
    List<Object[]> countGroupedByBuilding();

    @Query("select count(b) from Booking b where b.flat.id = :flatId and b.status = 'ACTIVE'")
    long countActiveByFlatId(@Param("flatId") UUID flatId);

    @Query(
            "select coalesce(sum(b.considerationAmt),0) from Booking b where b.builder.id = :builderId and "
                    + "b.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveConsideration(@Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(b.considerationAmt),0) from Booking b where b.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveConsiderationAll();

    List<Booking> findTop10ByOrderByCreatedAtDesc();

    List<Booking> findTop20ByOrderByCreatedAtDesc();

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "order by b.createdAt desc",
            countQuery = "select count(b) from Booking b")
    Page<Booking> findAllForPlatformDashboard(Pageable pageable);

    @Query("select count(b) from Booking b where b.createdAt >= :since")
    long countCreatedSince(@Param("since") java.time.Instant since);

    List<Booking> findTop5ByBuilder_IdOrderByCreatedAtDesc(UUID builderId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.broker.id = :brokerId and b.builder.id = :builderId order by b.bookingDate desc")
    List<Booking> findByBroker_IdAndBuilder_IdForListUi(
            @Param("brokerId") UUID brokerId, @Param("builderId") UUID builderId);

    long countByBuilder_IdAndBookingDateBetween(UUID builderId, java.time.LocalDate start, java.time.LocalDate end);

    long countByBookingDateBetween(java.time.LocalDate start, java.time.LocalDate end);

    @Query(
            "select b from Booking b join fetch b.client where b.builder.id = :builderId and b.status = 'ACTIVE' "
                    + "and b.flat.id in :flatIds order by b.bookingDate desc")
    List<Booking> findActiveWithClientByFlatIds(
            @Param("builderId") UUID builderId, @Param("flatIds") Collection<UUID> flatIds);

    @Query(
            "select b from Booking b join fetch b.flat f join fetch f.building bl where b.builder.id = :builderId "
                    + "and b.client.id = :clientId and b.status = 'ACTIVE' order by bl.buildingName, f.flatNumber")
    List<Booking> findActiveByClientWithFlatAndBuilding(
            @Param("builderId") UUID builderId, @Param("clientId") UUID clientId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.builder.id = :builderId and (b.status is null or b.status <> 'CANCELLED') "
                    + "order by f.building.buildingName, f.flatNumber")
    List<Booking> findActiveForPaymentSchedule(@Param("builderId") UUID builderId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building bl "
                    + "where b.builder.id = :builderId and (b.status is null or b.status <> 'CANCELLED') "
                    + "and bl.id = :buildingId "
                    + "order by f.flatNumber")
    List<Booking> findActiveForPaymentScheduleByBuilding(
            @Param("builderId") UUID builderId, @Param("buildingId") UUID buildingId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building where b.id = :id "
                    + "and b.builder.id = :builderId")
    Optional<Booking> findByIdAndBuilder_IdForSchedule(@Param("id") UUID id, @Param("builderId") UUID builderId);
}
