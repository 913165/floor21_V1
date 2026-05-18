package com.floor21.repository;

import com.floor21.entity.BookingSlabPayment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BookingSlabPaymentRepository extends JpaRepository<BookingSlabPayment, UUID> {

    List<BookingSlabPayment> findByPaymentSlab_IdOrderByPaymentDateAscSortOrderAscIdAsc(UUID paymentSlabId);

    @Query(
            """
            select p from BookingSlabPayment p
            join fetch p.paymentSlab s
            where s.booking.id = :bookingId
            order by s.sortOrder asc, s.id asc, p.paymentDate asc, p.sortOrder asc, p.id asc
            """)
    List<BookingSlabPayment> findByBookingIdOrderBySlabAndDate(@Param("bookingId") UUID bookingId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            "delete from BookingSlabPayment p where p.paymentSlab.id = :slabId and p.id not in :keepIds")
    int deleteByPaymentSlab_IdAndIdNotIn(
            @Param("slabId") UUID slabId, @Param("keepIds") Collection<UUID> keepIds);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("delete from BookingSlabPayment p where p.paymentSlab.id = :slabId")
    int deleteByPaymentSlab_Id(@Param("slabId") UUID slabId);
}
