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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "receipt_number", length = 64)
    private String receiptNumber;

    @Column(name = "receipt_serial", nullable = false)
    private Integer receiptSerial;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    /** Total for the receipt (sum of allocation lines). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_consideration", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountConsideration = BigDecimal.ZERO;

    @Column(name = "amount_extra_charges", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountExtraCharges = BigDecimal.ZERO;

    @Column(name = "amount_interest_agreement", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountInterestAgreement = BigDecimal.ZERO;

    @Column(name = "amount_interest_gst", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountInterestGst = BigDecimal.ZERO;

    @Column(name = "amount_tds", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountTds = BigDecimal.ZERO;

    @Column(name = "amount_gst_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountGstComponent = BigDecimal.ZERO;

    @Column(name = "payment_mode", length = 50)
    private String paymentMode;

    @Column(name = "cheque_no", length = 50)
    private String chequeNo;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "deposit_account", length = 200)
    private String depositAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_bank_id")
    private Bank depositBank;

    @Column(nullable = false)
    private Boolean dishonoured = Boolean.FALSE;

    @Column(name = "entered_by_display", length = 200)
    private String enteredByDisplay;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at")
    private Instant createdAt;
}
