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
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "left join fetch b.executive "
                            + "where b.builder.id = :builderId order by b.bookingDate desc, b.createdAt desc",
            countQuery = "select count(b) from Booking b where b.builder.id = :builderId")
    Page<Booking> findByBuilder_IdForListUi(@Param("builderId") UUID builderId, Pageable pageable);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "left join fetch b.executive "
                            + "where b.builder.id = :builderId order by b.bookingDate desc, b.createdAt desc")
    List<Booking> findByBuilder_IdForListUi(@Param("builderId") UUID builderId);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "left join fetch b.executive join fetch b.builder br where br.platformAdmin = false "
                            + "order by lower(br.companyName), b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b join b.builder br where br.platformAdmin = false")
    Page<Booking> findAllForPlatformAdminListUi(Pageable pageable);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "left join fetch b.executive join fetch b.builder br where br.platformAdmin = false "
                            + "order by lower(br.companyName), b.bookingDate desc, b.createdAt desc")
    List<Booking> findAllForPlatformAdminListUi();

    @Query(
            value =
                    "select b from Booking b join fetch b.client c join fetch b.flat f join fetch f.building bl "
                            + "left join fetch b.executive join fetch b.builder br where br.platformAdmin = false and ("
                            + "lower(b.bookingCode) like lower(concat('%', :q, '%')) or "
                            + "lower(concat(coalesce(c.firstName, ''), ' ', coalesce(c.lastName, ''))) like "
                            + "lower(concat('%', :q, '%')) or "
                            + "lower(f.flatNumber) like lower(concat('%', :q, '%')) or "
                            + "lower(bl.buildingName) like lower(concat('%', :q, '%')) or "
                            + "lower(br.companyName) like lower(concat('%', :q, '%'))) "
                            + "order by lower(br.companyName), b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b join b.client c join b.flat f join f.building bl join b.builder br "
                            + "where br.platformAdmin = false and ("
                            + "lower(b.bookingCode) like lower(concat('%', :q, '%')) or "
                            + "lower(concat(coalesce(c.firstName, ''), ' ', coalesce(c.lastName, ''))) like "
                            + "lower(concat('%', :q, '%')) or "
                            + "lower(f.flatNumber) like lower(concat('%', :q, '%')) or "
                            + "lower(bl.buildingName) like lower(concat('%', :q, '%')) or "
                            + "lower(br.companyName) like lower(concat('%', :q, '%')))")
    Page<Booking> searchAllForPlatformAdmin(@Param("q") String q, Pageable pageable);

    @Query(
            value =
                    "select b from Booking b join fetch b.client c join fetch b.flat f join fetch f.building bl "
                            + "left join fetch b.executive "
                            + "where b.builder.id = :builderId and ("
                            + "lower(b.bookingCode) like lower(concat('%', :q, '%')) or "
                            + "lower(concat(coalesce(c.firstName, ''), ' ', coalesce(c.lastName, ''))) like "
                            + "lower(concat('%', :q, '%')) or "
                            + "lower(f.flatNumber) like lower(concat('%', :q, '%')) or "
                            + "lower(bl.buildingName) like lower(concat('%', :q, '%'))) "
                            + "order by b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b join b.client c join b.flat f join f.building bl "
                            + "where b.builder.id = :builderId and ("
                            + "lower(b.bookingCode) like lower(concat('%', :q, '%')) or "
                            + "lower(concat(coalesce(c.firstName, ''), ' ', coalesce(c.lastName, ''))) like "
                            + "lower(concat('%', :q, '%')) or "
                            + "lower(f.flatNumber) like lower(concat('%', :q, '%')) or "
                            + "lower(bl.buildingName) like lower(concat('%', :q, '%')))")
    Page<Booking> searchByBuilder_IdForListUi(
            @Param("builderId") UUID builderId, @Param("q") String q, Pageable pageable);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "where b.builder.id = :builderId and f.building.id in :buildingIds "
                            + "order by b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b join b.flat f "
                            + "where b.builder.id = :builderId and f.building.id in :buildingIds")
    Page<Booking> findByBuilder_IdAndFlat_Building_IdInForListUi(
            @Param("builderId") UUID builderId,
            @Param("buildingIds") Collection<UUID> buildingIds,
            Pageable pageable);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "left join fetch b.executive join fetch b.builder br where b.id = :id and br.platformAdmin = false")
    Optional<Booking> findByIdForPlatformAdminView(@Param("id") UUID id);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "where b.builder.id = :builderId and b.executive.id = :executiveId "
                            + "order by b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b where b.builder.id = :builderId and b.executive.id = :executiveId")
    Page<Booking> findByBuilder_IdAndExecutive_IdForListUi(
            @Param("builderId") UUID builderId, @Param("executiveId") UUID executiveId, Pageable pageable);

    @Query(
            value =
                    "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                            + "where b.builder.id = :builderId and b.executive.id = :executiveId "
                            + "and f.building.id in :buildingIds "
                            + "order by b.bookingDate desc, b.createdAt desc",
            countQuery =
                    "select count(b) from Booking b join b.flat f "
                            + "where b.builder.id = :builderId and b.executive.id = :executiveId "
                            + "and f.building.id in :buildingIds")
    Page<Booking> findByBuilder_IdAndExecutive_IdAndFlat_Building_IdInForListUi(
            @Param("builderId") UUID builderId,
            @Param("executiveId") UUID executiveId,
            @Param("buildingIds") Collection<UUID> buildingIds,
            Pageable pageable);

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

    @Query(
            "select count(b) from Booking b where b.flat.building.id = :buildingId and b.status = 'ACTIVE'")
    long countActiveByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("select b.flat.building.id, count(b) from Booking b group by b.flat.building.id")
    List<Object[]> countGroupedByBuilding();

    @Query(
            "select b.flat.building.id, count(b) from Booking b where b.status = 'ACTIVE' group by b.flat.building.id")
    List<Object[]> countActiveGroupedByBuilding();

    @Query("select count(b) from Booking b where b.flat.id = :flatId and b.status = 'ACTIVE'")
    long countActiveByFlatId(@Param("flatId") UUID flatId);

    @Query(
            "select count(b) from Booking b where b.flat.id = :flatId and b.executive.id = :executiveId "
                    + "and b.status = 'ACTIVE'")
    long countActiveByFlatIdAndExecutive_Id(
            @Param("flatId") UUID flatId, @Param("executiveId") UUID executiveId);

    @Query("select count(b) from Booking b where b.flat.id = :flatId")
    long countByFlatId(@Param("flatId") UUID flatId);

    List<Booking> findByFlat_Id(UUID flatId);

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

    /** Highest F21-{year}-#### suffix for the given year prefix (e.g. {@code F21-2026-%}). */
    @Query(
            value =
                    "select coalesce(max(cast(substring(booking_code from 10) as integer)), 0) "
                            + "from bookings where booking_code like :yearPrefix",
            nativeQuery = true)
    long maxBookingCodeSequenceForYear(@Param("yearPrefix") String yearPrefix);

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

    @Query("select count(b) from Booking b where b.executive.id = :userId")
    long countByExecutive_Id(@Param("userId") UUID userId);
}
