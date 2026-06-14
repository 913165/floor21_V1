package com.floor21.repository;

import com.floor21.entity.Cancellation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancellationRepository extends JpaRepository<Cancellation, UUID> {

    List<Cancellation> findByBuilder_IdOrderByCancelDateDesc(UUID builderId);

    void deleteByBooking_Id(UUID bookingId);
}
