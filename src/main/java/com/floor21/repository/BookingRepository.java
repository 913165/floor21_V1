package com.floor21.repository;

import com.floor21.entity.Booking;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByBuilder_IdOrderByBookingDateDescCreatedAtDesc(UUID builderId);

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

    List<Booking> findByBroker_IdAndBuilder_IdOrderByBookingDateDesc(UUID brokerId, UUID builderId);

    long countByBuilder_IdAndBookingDateBetween(UUID builderId, java.time.LocalDate start, java.time.LocalDate end);
}
