package com.floor21.repository;

import com.floor21.entity.BookingOwner;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingOwnerRepository extends JpaRepository<BookingOwner, UUID> {

    @Query(
            """
            select bo from BookingOwner bo
            join fetch bo.client
            where bo.booking.id = :bookingId
            order by bo.sortOrder asc, bo.id asc
            """)
    List<BookingOwner> findByBooking_IdWithClientOrderBySortOrderAsc(@Param("bookingId") UUID bookingId);

    void deleteByBooking_IdAndRole(UUID bookingId, String role);

    @Query(
            """
            select distinct b from Booking b
            join fetch b.client
            join fetch b.flat f
            join fetch f.building
            where b.builder.id = :builderId
              and b.status = 'ACTIVE'
              and (
                b.client.id = :clientId
                or exists (
                  select 1 from BookingOwner bo
                  where bo.booking.id = b.id and bo.client.id = :clientId
                )
              )
            order by f.building.buildingName, f.flatNumber
            """)
    List<com.floor21.entity.Booking> findActiveByClientOrCoOwner(
            @Param("builderId") UUID builderId, @Param("clientId") UUID clientId);

    long countByClient_Id(UUID clientId);

    void deleteByClient_Id(UUID clientId);
}
