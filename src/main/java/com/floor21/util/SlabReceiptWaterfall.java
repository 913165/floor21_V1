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
            BigDecimal receiptTotal = pool;
            BigDecimal gstPool =
                    receipt.getAmountGstComponent() != null ? receipt.getAmountGstComponent() : ZERO;
            BigDecimal gstRemaining = gstPool;
            LocalDate payDate =
                    receipt.getReceiptDate() != null ? receipt.getReceiptDate() : LocalDate.now();
            String reference = buildReceiptReference(receipt);
            String chequeLabel = buildChequeLabel(receipt);
            String remark = receiptRemarks(receipt);

            for (int i = 0; i < slabs.size() && pool.compareTo(ZERO) > 0; i++) {
                if (remainingDue[i].compareTo(ZERO) <= 0) {
                    continue;
                }
                BigDecimal apply = pool.min(remainingDue[i]);
                if (apply.compareTo(ZERO) <= 0) {
                    continue;
                }
                boolean exhaustsPool = pool.subtract(apply).compareTo(ZERO) <= 0;
                BigDecimal gstApply = gstForSlice(gstPool, gstRemaining, receiptTotal, apply, exhaustsPool);
                gstRemaining = gstRemaining.subtract(gstApply);
                addSlice(
                        bySlab,
                        slabs.get(i).getId(),
                        receipt.getId(),
                        payDate,
                        apply,
                        gstApply,
                        reference,
                        remark,
                        chequeLabel);
                remainingDue[i] = remainingDue[i].subtract(apply);
                pool = pool.subtract(apply);
            }
            if (pool.compareTo(ZERO) > 0) {
                BookingPaymentSlab last = slabs.get(slabs.size() - 1);
                addSlice(
                        bySlab,
                        last.getId(),
                        receipt.getId(),
                        payDate,
                        pool,
                        gstRemaining.max(ZERO),
                        reference,
                        remark,
                        chequeLabel);
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
        return buildChequeLabel(receipt);
    }

    /** Cheque / ref. label for the payment schedule (legacy: {@code Chq No:097552}). */
    public static String buildChequeLabel(Receipt receipt) {
        if (receipt.getChequeNo() != null && !receipt.getChequeNo().isBlank()) {
            return "Chq No:" + receipt.getChequeNo().trim();
        }
        if (receipt.getPaymentMode() != null && !receipt.getPaymentMode().isBlank()) {
            return receipt.getPaymentMode().trim();
        }
        return null;
    }

    private static String receiptRemarks(Receipt receipt) {
        if (receipt.getRemarks() == null || receipt.getRemarks().isBlank()) {
            return null;
        }
        return receipt.getRemarks().trim();
    }

    private static BigDecimal gstForSlice(
            BigDecimal gstPool,
            BigDecimal gstRemaining,
            BigDecimal receiptTotal,
            BigDecimal apply,
            boolean exhaustsPool) {
        if (gstPool.compareTo(ZERO) <= 0 || apply.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        if (exhaustsPool) {
            return gstRemaining.max(ZERO);
        }
        if (receiptTotal.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return gstPool
                .multiply(apply)
                .divide(receiptTotal, 2, RoundingMode.HALF_UP)
                .max(ZERO);
    }

    private static void addSlice(
            Map<UUID, List<ReceiptSlabAllocationSlice>> bySlab,
            UUID slabId,
            UUID receiptId,
            LocalDate payDate,
            BigDecimal amount,
            BigDecimal gstAmount,
            String reference,
            String remark,
            String chequeLabel) {
        bySlab.get(slabId)
                .add(
                        new ReceiptSlabAllocationSlice(
                                slabId,
                                receiptId,
                                payDate,
                                amount.setScale(2, RoundingMode.HALF_UP),
                                gstAmount != null
                                        ? gstAmount.setScale(2, RoundingMode.HALF_UP)
                                        : ZERO,
                                reference,
                                remark,
                                chequeLabel));
    }
}
