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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "flats")
public class Flat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "flat_number", nullable = false, length = 20)
    private String flatNumber;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "unit_number", nullable = false)
    private Integer unitNumber;

    @Column(name = "bhk_type", nullable = false, length = 20)
    private String bhkType;

    @Column(name = "area_sqft", precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(name = "base_price", precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Column(length = 20)
    private String status = "AVAILABLE";

    @Column(name = "is_parking")
    private Boolean parking = false;

    /** When set, this unit is the upper/linked half of a duplex; book on {@link #duplexPrimaryFlatId}. */
    @Column(name = "duplex_primary_flat_id")
    private UUID duplexPrimaryFlatId;

    /** When set, this lower-floor unit is the bookable primary of a vertical duplex. */
    @Column(name = "duplex_secondary_flat_id")
    private UUID duplexSecondaryFlatId;

    /** When set, this unit was absorbed by a same-floor merge and is hidden from the grid. */
    @Column(name = "merged_into_flat_id")
    private UUID mergedIntoFlatId;

    /** When set, this unit absorbed another flat on the same floor (restorable). */
    @Column(name = "merged_absorbed_flat_id")
    private UUID mergedAbsorbedFlatId;

    @Column(name = "pre_merge_bhk_type", length = 20)
    private String preMergeBhkType;

    @Column(name = "pre_merge_area_sqft", precision = 10, scale = 2)
    private BigDecimal preMergeAreaSqft;

    @Column(name = "pre_merge_base_price", precision = 15, scale = 2)
    private BigDecimal preMergeBasePrice;

    @Column(name = "pre_merge_status", length = 20)
    private String preMergeStatus;

    @Column(name = "created_at")
    private Instant createdAt;
}
