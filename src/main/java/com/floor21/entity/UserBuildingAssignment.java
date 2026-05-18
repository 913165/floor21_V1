package com.floor21.entity;

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
@Table(name = "user_building_assignments")
public class UserBuildingAssignment {

    @EmbeddedId
    private AssignmentId id = new AssignmentId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("buildingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    public UserBuildingAssignment(User user, Building building) {
        this.user = user;
        this.building = building;
        this.id = new AssignmentId(user.getId(), building.getId());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AssignmentId implements Serializable {
        private UUID userId;
        private UUID buildingId;

        public AssignmentId(UUID userId, UUID buildingId) {
            this.userId = userId;
            this.buildingId = buildingId;
        }
    }
}
