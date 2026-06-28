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
@Table(name = "slabs")
public class Slab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "slab_name", length = 100)
    private String slabName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "suggested_percent", precision = 9, scale = 4)
    private BigDecimal suggestedPercent;

    /** Optional default due date copied to client milestone rows from this template. */
    @Column(name = "default_due_date")
    private LocalDate defaultDueDate;

    @Column(name = "rate_per_sqft", precision = 10, scale = 2)
    private BigDecimal ratePerSqft;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;
}
