package com.floor21.repository;

import com.floor21.entity.BookingPaymentSlab;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingPaymentSlabRepository extends JpaRepository<BookingPaymentSlab, UUID> {

    List<BookingPaymentSlab> findByBooking_IdOrderBySortOrderAscIdAsc(UUID bookingId);

    java.util.Optional<BookingPaymentSlab> findByIdAndBooking_Id(UUID id, UUID bookingId);

    long countByBooking_Id(UUID bookingId);

    void deleteByBooking_Id(UUID bookingId);
}
