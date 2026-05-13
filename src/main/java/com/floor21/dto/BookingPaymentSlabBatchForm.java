package com.floor21.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class BookingPaymentSlabBatchForm {

    private UUID bookingId;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private UUID id;
        /** Day of month (1–31); use with dueMonth and dueYear, or all null for no date. */
        private Integer dueDay;
        /** Month (1–12). */
        private Integer dueMonth;
        /** Four-digit year. */
        private Integer dueYear;
        /** Slab description (editable per booking row). */
        private String milestoneLabel;
        private BigDecimal percent;
        /** Portion from consideration × percent, or user-entered override. */
        private BigDecimal agreedAmount;
        private BigDecimal extraAmount;
    }
}
