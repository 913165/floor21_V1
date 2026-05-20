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
@Table(name = "partner_flat_assignments")
public class PartnerFlatAssignment {

    @EmbeddedId
    private AssignmentId id = new AssignmentId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("flatId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    public PartnerFlatAssignment(User user, Flat flat, Building building) {
        this.user = user;
        this.flat = flat;
        this.building = building;
        this.id = new AssignmentId(user.getId(), flat.getId());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AssignmentId implements Serializable {
        private UUID userId;
        private UUID flatId;

        public AssignmentId(UUID userId, UUID flatId) {
            this.userId = userId;
            this.flatId = flatId;
        }
    }
}
