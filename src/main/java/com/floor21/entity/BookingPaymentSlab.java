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
@Table(name = "booking_payment_slabs")
public class BookingPaymentSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private PaymentSlabTemplate template;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "milestone_label", nullable = false, length = 800)
    private String milestoneLabel;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 9, scale = 4)
    private BigDecimal percent;

    @Column(name = "extra_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal extraAmount = BigDecimal.ZERO;

    /** Portion from flat consideration × percent ÷ 100 (excluding extra_amount). */
    @Column(name = "agreed_amount", precision = 15, scale = 2)
    private BigDecimal agreedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
