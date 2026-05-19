package com.floor21.repository;

import com.floor21.entity.BookingPaymentSlab;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingPaymentSlabRepository extends JpaRepository<BookingPaymentSlab, UUID> {

    @Query(
            """
            select s from BookingPaymentSlab s
            left join fetch s.template
            where s.booking.id = :bookingId
            order by s.sortOrder asc, s.id asc
            """)
    List<BookingPaymentSlab> findByBooking_IdOrderBySortOrderAscIdAsc(@Param("bookingId") UUID bookingId);

    java.util.Optional<BookingPaymentSlab> findByIdAndBooking_Id(UUID id, UUID bookingId);

    long countByBooking_Id(UUID bookingId);

    void deleteByBooking_Id(UUID bookingId);
}
