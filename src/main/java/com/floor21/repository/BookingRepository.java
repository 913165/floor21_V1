package com.floor21.repository;

import com.floor21.entity.Booking;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.builder.id = :builderId order by b.bookingDate desc, b.createdAt desc")
    List<Booking> findByBuilder_IdForListUi(@Param("builderId") UUID builderId);

    Optional<Booking> findByIdAndBuilder_Id(UUID id, UUID builderId);

    @Query("select count(b) from Booking b where b.builder.id = :builderId and b.status = 'ACTIVE'")
    long countActiveByBuilder(@Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(b.considerationAmt),0) from Booking b where b.builder.id = :builderId and "
                    + "b.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveConsideration(@Param("builderId") UUID builderId);

    @Query(
            "select coalesce(sum(b.considerationAmt),0) from Booking b where b.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveConsiderationAll();

    List<Booking> findTop10ByOrderByCreatedAtDesc();

    List<Booking> findTop5ByBuilder_IdOrderByCreatedAtDesc(UUID builderId);

    @Query(
            "select b from Booking b join fetch b.client join fetch b.flat f join fetch f.building "
                    + "where b.broker.id = :brokerId and b.builder.id = :builderId order by b.bookingDate desc")
    List<Booking> findByBroker_IdAndBuilder_IdForListUi(
            @Param("brokerId") UUID brokerId, @Param("builderId") UUID builderId);

    long countByBuilder_IdAndBookingDateBetween(UUID builderId, java.time.LocalDate start, java.time.LocalDate end);

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
