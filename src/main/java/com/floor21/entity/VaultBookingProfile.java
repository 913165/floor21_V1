package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vault_booking_profiles")
public class VaultBookingProfile {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    /** Full deal / total consideration (e.g. 1.5 Cr). */
    @Column(name = "total_consideration", precision = 15, scale = 2)
    private BigDecimal totalConsideration;

    /** Registered or SLA / agreement base value (e.g. 1.2 Cr). */
    @Column(name = "register_value", precision = 15, scale = 2)
    private BigDecimal registerValue;

    /** Gap amount (e.g. 30 Lakh); typically total consideration minus register value. */
    @Column(name = "extra_amount", precision = 15, scale = 2)
    private BigDecimal extraAmount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
