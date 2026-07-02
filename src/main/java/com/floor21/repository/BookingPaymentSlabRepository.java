package com.floor21.repository;

import com.floor21.entity.BookingPaymentSlab;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingPaymentSlabRepository extends JpaRepository<BookingPaymentSlab, UUID> {

    @Query(
            """
            select s.booking.id,
                   coalesce(sum(coalesce(s.agreedAmount, 0) + coalesce(s.extraAmount, 0)), 0)
            from BookingPaymentSlab s
            where s.booking.id in :bookingIds
            group by s.booking.id
            """)
    List<Object[]> sumScheduleDueByBookingIds(@Param("bookingIds") Collection<UUID> bookingIds);

    @Query(
            """
            select s from BookingPaymentSlab s
            left join fetch s.template
            where s.booking.id = :bookingId
            order by s.sortOrder asc, s.id asc
            """)
    List<BookingPaymentSlab> findByBooking_IdOrderBySortOrderAscIdAsc(@Param("bookingId") UUID bookingId);

    @Query(
            """
            select s from BookingPaymentSlab s
            where s.booking.id in :bookingIds
            order by s.booking.id asc, s.sortOrder asc, s.id asc
            """)
    List<BookingPaymentSlab> findByBooking_IdInOrderByBooking_IdAscSortOrderAscIdAsc(
            @Param("bookingIds") Collection<UUID> bookingIds);

    java.util.Optional<BookingPaymentSlab> findByIdAndBooking_Id(UUID id, UUID bookingId);

    long countByBooking_Id(UUID bookingId);

    void deleteByBooking_Id(UUID bookingId);
}
