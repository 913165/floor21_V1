package com.floor21.util;

import com.floor21.dto.ReceiptSlabAllocationSlice;
import com.floor21.entity.BookingPaymentSlab;
import com.floor21.entity.Receipt;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Allocates receipt totals across booking slabs in order (in memory only). */
public final class SlabReceiptWaterfall {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private SlabReceiptWaterfall() {}

    public static Map<UUID, List<ReceiptSlabAllocationSlice>> allocate(
            List<BookingPaymentSlab> slabs, List<Receipt> receipts) {
        Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab = new LinkedHashMap<>();
        if (slabs.isEmpty()) {
            return bySlab;
        }
        for (BookingPaymentSlab slab : slabs) {
            bySlab.put(slab.getId(), new ArrayList<>());
        }

        BigDecimal[] remainingDue = new BigDecimal[slabs.size()];
        for (int i = 0; i < slabs.size(); i++) {
            remainingDue[i] = slabDue(slabs.get(i));
        }

        for (Receipt receipt : receipts) {
            BigDecimal pool = receipt.getAmount() != null ? receipt.getAmount() : ZERO;
            if (pool.compareTo(ZERO) <= 0) {
                continue;
            }
            LocalDate payDate =
                    receipt.getReceiptDate() != null ? receipt.getReceiptDate() : LocalDate.now();
            String reference = buildReceiptReference(receipt);

            for (int i = 0; i < slabs.size() && pool.compareTo(ZERO) > 0; i++) {
                if (remainingDue[i].compareTo(ZERO) <= 0) {
                    continue;
                }
                BigDecimal apply = pool.min(remainingDue[i]);
                if (apply.compareTo(ZERO) <= 0) {
                    continue;
                }
                addSlice(bySlab, slabs.get(i).getId(), receipt.getId(), payDate, apply, reference);
                remainingDue[i] = remainingDue[i].subtract(apply);
                pool = pool.subtract(apply);
            }
            if (pool.compareTo(ZERO) > 0) {
                BookingPaymentSlab last = slabs.get(slabs.size() - 1);
                addSlice(bySlab, last.getId(), receipt.getId(), payDate, pool, reference);
            }
        }
        return bySlab;
    }

    public static BigDecimal slabDue(BookingPaymentSlab slab) {
        BigDecimal agreed = slab.getAgreedAmount() != null ? slab.getAgreedAmount() : ZERO;
        BigDecimal extra = slab.getExtraAmount() != null ? slab.getExtraAmount() : ZERO;
        return agreed.add(extra);
    }

    public static String buildReceiptReference(Receipt receipt) {
        StringBuilder sb = new StringBuilder();
        if (receipt.getReceiptNumber() != null && !receipt.getReceiptNumber().isBlank()) {
            sb.append("Rec. ").append(receipt.getReceiptNumber().trim());
        }
        if (receipt.getPaymentMode() != null && !receipt.getPaymentMode().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(receipt.getPaymentMode().trim());
        }
        if (receipt.getChequeNo() != null && !receipt.getChequeNo().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Chq No:").append(receipt.getChequeNo().trim());
        }
        if (receipt.getBankName() != null && !receipt.getBankName().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(receipt.getBankName().trim());
        }
        return sb.length() > 0 ? sb.toString() : "Receipt";
    }

    private static void addSlice(
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab,
            UUID slabId,
            UUID receiptId,
            LocalDate payDate,
            BigDecimal amount,
            String reference) {
        bySlab.get(slabId)
                .add(
                        new ReceiptSlabAllocationSlice(
                                slabId,
                                receiptId,
                                payDate,
                                amount.setScale(2, RoundingMode.HALF_UP),
                                reference));
    }
}
