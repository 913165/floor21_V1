CREATE TABLE user_building_assignments (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    building_id  UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, building_id)
);

CREATE INDEX idx_user_building_assignments_building ON user_building_assignments(building_id);
