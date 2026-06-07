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
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @Column(name = "building_name", nullable = false, length = 200)
    private String buildingName;

    @Column(name = "total_floors", nullable = false)
    private Integer totalFloors;

    @Column(name = "parking_floors")
    private Integer parkingFloors = 0;

    @Column(name = "flats_per_floor", nullable = false)
    private Integer flatsPerFloor;

    @Column(name = "bhk1_per_floor")
    private Integer bhk1PerFloor = 0;

    @Column(name = "bhk2_per_floor")
    private Integer bhk2PerFloor = 0;

    @Column(name = "bhk3_per_floor")
    private Integer bhk3PerFloor = 0;

    /** JSON map of unit-type counts per floor (Studio, 1BHK … 7BHK, Penthouse). */
    @Column(name = "bhk_mix_per_floor", columnDefinition = "TEXT")
    private String bhkMixPerFloor;

    /** JSON map of floor number → parking slot count / configured flag. */
    @Column(name = "parking_floor_config", columnDefinition = "TEXT")
    private String parkingFloorConfig;

    /** JSON map of unit type → default super built-up, carpet, balcony, and price. */
    @Column(name = "unit_type_defaults", columnDefinition = "TEXT")
    private String unitTypeDefaults;

    /** JSON map of layout column type (A, B, C…) → default areas and price. */
    @Column(name = "column_type_defaults", columnDefinition = "TEXT")
    private String columnTypeDefaults;

    /** Comma-separated floor numbers to omit from the grid (e.g. {@code 13} or {@code 4,13}). */
    @Column(name = "skipped_floor_numbers", length = 200)
    private String skippedFloorNumbers;

    /** Bound from add/edit building form; not persisted directly. */
    @Transient
    private Map<String, Integer> bhkPerFloor = new LinkedHashMap<>();

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "is_active")
    private Boolean active = true;

    /** Legacy column; access is controlled in Vault config (user + building). */
    @Column(name = "vault_enabled", nullable = false)
    private Boolean vaultEnabled = false;

    /** Web path under context, e.g. {@code media/buildings/{id}/1BHK.png}; null if not uploaded. */
    @Column(name = "floor_plan_1bhk", length = 500)
    private String floorPlan1Bhk;

    @Column(name = "floor_plan_2bhk", length = 500)
    private String floorPlan2Bhk;

    @Column(name = "floor_plan_3bhk", length = 500)
    private String floorPlan3Bhk;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
