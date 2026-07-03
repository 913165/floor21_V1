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
            BigDecimal gstPool = gstComponentTotal(receipt);
            BigDecimal pool = slabAllocationPool(receipt);
            if (pool.compareTo(ZERO) <= 0 && gstPool.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal slabPoolTotal = pool;
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
                BigDecimal gstApply =
                        gstForSlice(gstPool, gstRemaining, slabPoolTotal, apply, exhaustsPool);
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
            } else if (gstRemaining.compareTo(ZERO) > 0) {
                BookingPaymentSlab target = firstSlabForGstOnly(slabs, remainingDue);
                addSlice(
                        bySlab,
                        target.getId(),
                        receipt.getId(),
                        payDate,
                        ZERO,
                        gstRemaining,
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

    /** Receipt amount applied to milestone balance (excludes GST components). */
    static BigDecimal slabAllocationPool(Receipt receipt) {
        BigDecimal total = receipt.getAmount() != null ? receipt.getAmount() : ZERO;
        return total.subtract(gstComponentTotal(receipt)).max(ZERO);
    }

    static BigDecimal gstComponentTotal(Receipt receipt) {
        BigDecimal gst = receipt.getAmountGstComponent() != null ? receipt.getAmountGstComponent() : ZERO;
        BigDecimal interestGst =
                receipt.getAmountInterestGst() != null ? receipt.getAmountInterestGst() : ZERO;
        return gst.add(interestGst);
    }

    private static BookingPaymentSlab firstSlabForGstOnly(
            List<BookingPaymentSlab> slabs, BigDecimal[] remainingDue) {
        for (int i = 0; i < slabs.size(); i++) {
            if (remainingDue[i].compareTo(ZERO) > 0) {
                return slabs.get(i);
            }
        }
        return slabs.get(slabs.size() - 1);
    }

    public static String buildReceiptReference(Receipt receipt) {
        return buildChequeLabel(receipt);
    }

    /** Payment mode label for the slab schedule (e.g. NEFT, Cheque with number). */
    public static String buildChequeLabel(Receipt receipt) {
        if (receipt == null) {
            return null;
        }
        String mode = normalizeToken(receipt.getPaymentMode());
        String ref = normalizeToken(receipt.getChequeNo());
        if (mode != null) {
            if (isChequeMode(mode)) {
                if (ref != null) {
                    return "Chq No:" + ref;
                }
                return "Cheque";
            }
            return mode;
        }
        if (ref != null) {
            return "Chq No:" + ref;
        }
        return null;
    }

    private static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isChequeMode(String mode) {
        return "Cheque".equalsIgnoreCase(mode) || "CHQ".equalsIgnoreCase(mode);
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
            BigDecimal slabPoolTotal,
            BigDecimal apply,
            boolean exhaustsPool) {
        if (gstPool.compareTo(ZERO) <= 0 || apply.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        if (exhaustsPool) {
            return gstRemaining.max(ZERO);
        }
        if (slabPoolTotal.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return gstPool
                .multiply(apply)
                .divide(slabPoolTotal, 2, RoundingMode.HALF_UP)
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
