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
@Table(name = "user_project_assignments")
public class UserProjectAssignment {

    @EmbeddedId
    private AssignmentId id = new AssignmentId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("builderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @jakarta.persistence.Column(nullable = false, length = 50)
    private String role;

    public UserProjectAssignment(User user, Builder builder, String role) {
        this.user = user;
        this.builder = builder;
        this.role = role;
        this.id = new AssignmentId(user.getId(), builder.getId());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AssignmentId implements Serializable {
        private UUID userId;
        private UUID builderId;

        public AssignmentId(UUID userId, UUID builderId) {
            this.userId = userId;
            this.builderId = builderId;
        }
    }
}
