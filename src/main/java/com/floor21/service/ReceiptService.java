package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.Receipt;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final BookingRepository bookingRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public java.math.BigDecimal totalForBooking(UUID bookingId) {
        return receiptRepository.sumAmountForBooking(bookingId, TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public List<Receipt> listForBooking(UUID bookingId) {
        return receiptRepository.findByBooking_IdAndBuilder_IdOrderByReceiptDateDesc(
                bookingId, TenantContext.requireBuilderId());
    }

    @Transactional
    public Receipt save(UUID bookingId, Receipt form) {
        UUID builderId = TenantContext.requireBuilderId();
        Booking booking =
                bookingRepository
                        .findByIdAndBuilder_Id(bookingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var builder = builderRepository.findById(builderId).orElseThrow();
        Receipt r = new Receipt();
        r.setBuilder(builder);
        r.setBooking(booking);
        r.setReceiptDate(form.getReceiptDate());
        r.setAmount(form.getAmount());
        r.setPaymentMode(form.getPaymentMode());
        r.setChequeNo(form.getChequeNo());
        r.setBankName(form.getBankName());
        r.setRemarks(form.getRemarks());
        r.setCreatedAt(Instant.now());
        return receiptRepository.save(r);
    }
}
