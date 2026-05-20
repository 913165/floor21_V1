CREATE TABLE user_building_vault_access (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    building_id UUID NOT NULL REFERENCES buildings (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, building_id)
);

-- Migrate prior builder / user / building flags into explicit grants.
INSERT INTO user_building_vault_access (user_id, building_id, enabled)
SELECT u.id, b.id, TRUE
FROM users u
         JOIN buildings b ON b.builder_id = u.builder_id
         JOIN builders br ON br.id = u.builder_id
WHERE u.role = 'BUILDER_ADMIN'
  AND u.vault_access_enabled = TRUE
  AND b.vault_enabled = TRUE
  AND br.vault_enabled = TRUE
ON CONFLICT (user_id, building_id) DO NOTHING;
