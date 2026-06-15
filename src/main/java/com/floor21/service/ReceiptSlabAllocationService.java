package com.floor21.service;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Receipt;
import com.floor21.repository.ReceiptRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.SlabReceiptWaterfall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes how buyer receipts fill payment slabs (waterfall). Results are calculated on each load —
 * not stored in {@code booking_slab_payments}.
 */
@Service
@RequiredArgsConstructor
public class ReceiptSlabAllocationService {

    private final ReceiptRepository receiptRepository;
    private final BookingPaymentSlabService bookingPaymentSlabService;

    @Transactional
    public Map<UUID, List<ReceiptSlabAllocationSlice>> allocateBySlab(UUID bookingId) {
        UUID builderId = TenantContext.requireBuilderId();
        return allocateBySlab(bookingId, builderId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ReceiptSlabAllocationSlice>> allocateBySlab(UUID bookingId, UUID builderId) {
        List<BookingPaymentSlab> slabs =
                TenantContext.getBuilderIdOrNull() != null
                        ? bookingPaymentSlabService.listUniqueSlabsForSchedule(bookingId)
                        : bookingPaymentSlabService.listUniqueSlabsForScheduleReadOnly(bookingId, builderId);
        if (slabs.isEmpty()) {
            return Map.of();
        }
        List<Receipt> receipts =
                receiptRepository.findActiveByBooking_IdOrderByReceiptDateAsc(bookingId, builderId);
        return SlabReceiptWaterfall.allocate(slabs, receipts);
    }
}
