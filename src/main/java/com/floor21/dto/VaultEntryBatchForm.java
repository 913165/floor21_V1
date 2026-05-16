package com.floor21.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class VaultEntryBatchForm {

    private UUID bookingId;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        /** Vault entry id when updating; null for new. */
        private UUID id;
        private UUID paymentSlabId;
        private String paymentMode;
        private BigDecimal amount;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate entryDate;
        private String notes;
    }
}
