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
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id")
    private Broker broker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executive_id")
    private User executive;

    @Column(name = "file_no", length = 50)
    private String fileNo;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "consideration_amt", precision = 15, scale = 2)
    private BigDecimal considerationAmt = BigDecimal.ZERO;

    @Column(length = 100)
    private String scheme;

    @Column(name = "parking_info", columnDefinition = "TEXT")
    private String parkingInfo;

    @Column(columnDefinition = "TEXT")
    private String reference;

    @Column(name = "is_bare_flat")
    private Boolean bareFlat = false;

    @Column(columnDefinition = "TEXT")
    private String particulars;

    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
