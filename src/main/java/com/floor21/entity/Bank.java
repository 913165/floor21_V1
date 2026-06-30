package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "banks")
public class Bank {

    public static final String PURPOSE_INSTALMENT = "INSTALMENT";
    public static final String PURPOSE_GST = "GST";
    public static final String PURPOSE_RERA = "RERA";
    public static final String PURPOSE_PERSONAL = "PERSONAL";
    public static final String PURPOSE_JOINT = "JOINT";
    public static final String PURPOSE_CURRENT = "CURRENT";
    public static final String PURPOSE_PERSONAL_SAVINGS = "PERSONAL_SAVINGS";
    public static final String PURPOSE_OTHERS = "OTHERS";

    private static final Set<String> KNOWN_PURPOSES =
            Set.of(
                    PURPOSE_INSTALMENT,
                    PURPOSE_GST,
                    PURPOSE_RERA,
                    PURPOSE_PERSONAL,
                    PURPOSE_JOINT,
                    PURPOSE_CURRENT,
                    PURPOSE_PERSONAL_SAVINGS,
                    PURPOSE_OTHERS);

    public static boolean isKnownPurpose(String purpose) {
        return purpose != null && KNOWN_PURPOSES.contains(purpose.trim().toUpperCase());
    }

    public static String normalizePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return PURPOSE_INSTALMENT;
        }
        String normalized = purpose.trim().toUpperCase();
        return isKnownPurpose(normalized) ? normalized : PURPOSE_INSTALMENT;
    }

    public static String purposeDisplayLabel(String purpose) {
        return usedForDisplayLabel(purpose);
    }

    /** Label for bank list / form: which demand-letter column this account fills. */
    public static String usedForDisplayLabel(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return "Flat cost instalment";
        }
        return switch (purpose.trim().toUpperCase()) {
            case PURPOSE_INSTALMENT -> "Flat cost instalment";
            case PURPOSE_GST -> "GST";
            default -> "Other";
        };
    }

    public static boolean isDemandLetterPurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return true;
        }
        String normalized = purpose.trim().toUpperCase();
        return PURPOSE_INSTALMENT.equals(normalized) || PURPOSE_GST.equals(normalized);
    }

    public boolean isDemandLetterInstalmentAccount() {
        return PURPOSE_INSTALMENT.equalsIgnoreCase(accountPurpose);
    }

    public boolean isDemandLetterGstAccount() {
        return PURPOSE_GST.equalsIgnoreCase(accountPurpose);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @Column(name = "bank_name", nullable = false, length = 200)
    private String bankName;

    @Column(length = 200)
    private String branch;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 200)
    private String accountHolderName;

    /** Account classification (instalment, GST, RERA, personal, joint, etc.). */
    @Column(name = "account_purpose", nullable = false, length = 32)
    private String accountPurpose = "INSTALMENT";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
