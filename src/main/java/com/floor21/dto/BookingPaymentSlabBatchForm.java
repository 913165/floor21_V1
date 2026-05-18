package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class BookingPaymentSlabBatchForm {

    private UUID bookingId;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private UUID id;
        /** Slab due date, or null when not set. */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dueDate;
        /** Slab description (editable per booking row). */
        private String milestoneLabel;
        private BigDecimal percent;
        /** Portion from consideration × percent, or user-entered override. */
        private BigDecimal agreedAmount;
        private BigDecimal extraAmount;
        private List<PaymentLine> payments = new ArrayList<>();
    }

    @Data
    public static class PaymentLine {
        private UUID id;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate paymentDate;
        private BigDecimal amount;
        /** Mode, cheque no., or short note. */
        private String reference;
    }
}
