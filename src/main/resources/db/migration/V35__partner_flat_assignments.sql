CREATE TABLE partner_flat_assignments (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    flat_id     UUID NOT NULL REFERENCES flats(id) ON DELETE CASCADE,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, flat_id),
    CONSTRAINT uq_partner_flat_assignments_flat UNIQUE (flat_id)
);

CREATE INDEX idx_partner_flat_assignments_building ON partner_flat_assignments(building_id);
CREATE INDEX idx_partner_flat_assignments_user_building ON partner_flat_assignments(user_id, building_id);
