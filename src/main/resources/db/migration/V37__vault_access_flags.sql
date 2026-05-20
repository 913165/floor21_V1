ALTER TABLE builders
    ADD COLUMN vault_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ADD COLUMN vault_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing builder admins keep vault until platform admin turns it off per user.
UPDATE users
SET vault_access_enabled = TRUE
WHERE role = 'BUILDER_ADMIN';
