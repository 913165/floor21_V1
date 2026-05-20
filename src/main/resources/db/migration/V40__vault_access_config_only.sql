-- Vault access is only via Platform → Vault config (user_building_vault_access).
-- Legacy per-builder / per-building / per-user flags are off by default.

ALTER TABLE builders ALTER COLUMN vault_enabled SET DEFAULT FALSE;
UPDATE builders SET vault_enabled = FALSE;

ALTER TABLE buildings ALTER COLUMN vault_enabled SET DEFAULT FALSE;
UPDATE buildings SET vault_enabled = FALSE;

UPDATE users SET vault_access_enabled = FALSE;

-- Drop auto-grants from V39 backfill; re-add combinations in Vault config as needed.
DELETE FROM user_building_vault_access;
