-- Platform users can exist before they are linked to a project (via Partners).
ALTER TABLE users ALTER COLUMN builder_id DROP NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_builder_id_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email));
