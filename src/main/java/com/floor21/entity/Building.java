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
}
