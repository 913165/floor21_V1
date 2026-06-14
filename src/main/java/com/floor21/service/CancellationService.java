package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.Cancellation;
import com.floor21.entity.Flat;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.CancellationRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancellationService {

    private final CancellationRepository cancellationRepository;
    private final BookingRepository bookingRepository;
    private final FlatRepository flatRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Cancellation> list() {
        return cancellationRepository.findByBuilder_IdOrderByCancelDateDesc(TenantContext.requireBuilderId());
    }

    @Transactional
    public void cancelBooking(UUID bookingId, LocalDate cancelDate, String reason, BigDecimal refund) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_Id(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking already cancelled");
        }
        var builder = builderRepository.findById(builderId).orElseThrow();
        Cancellation c = new Cancellation();
        c.setBuilder(builder);
        c.setBooking(booking);
        c.setCancelDate(cancelDate);
        c.setReason(reason);
        c.setRefundAmount(refund != null ? refund : BigDecimal.ZERO);
        c.setCreatedAt(Instant.now());
        cancellationRepository.save(c);

        booking.setStatus("CANCELLED");
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);

        Flat flat = booking.getFlat();
        if (bookingRepository.countActiveByFlatId(flat.getId()) == 0) {
            flat.setStatus("AVAILABLE");
            flatRepository.save(flat);
        }
    }
}
