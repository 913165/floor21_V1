package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_building_vault_access")
public class UserBuildingVaultAccess {

    @EmbeddedId
    private GrantId id = new GrantId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("buildingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false)
    private Boolean enabled = true;

    public UserBuildingVaultAccess(User user, Building building) {
        this.user = user;
        this.building = building;
        this.id = new GrantId(user.getId(), building.getId());
        this.enabled = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GrantId implements Serializable {
        private UUID userId;
        private UUID buildingId;

        public GrantId(UUID userId, UUID buildingId) {
            this.userId = userId;
            this.buildingId = buildingId;
        }
    }
}
