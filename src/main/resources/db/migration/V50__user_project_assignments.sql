-- Users can belong to multiple projects (builders).
CREATE TABLE user_project_assignments (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    builder_id UUID NOT NULL REFERENCES builders(id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, builder_id)
);

CREATE INDEX idx_user_project_assignments_builder ON user_project_assignments(builder_id);

INSERT INTO user_project_assignments (user_id, builder_id, role)
SELECT id, builder_id, role
FROM users
WHERE builder_id IS NOT NULL;

UPDATE users SET builder_id = NULL WHERE builder_id IS NOT NULL;
