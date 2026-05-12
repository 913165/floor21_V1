package com.floor21.repository;

import com.floor21.entity.Receipt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    List<Receipt> findByBooking_IdAndBuilder_IdOrderByReceiptDateDesc(UUID bookingId, UUID builderId);

    @Query(
            "select coalesce(sum(r.amount),0) from Receipt r where r.booking.id = :bookingId and "
                    + "r.builder.id = :builderId")
    java.math.BigDecimal sumAmountForBooking(
            @Param("bookingId") UUID bookingId, @Param("builderId") UUID builderId);
}
