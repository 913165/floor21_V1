ALTER TABLE builders ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

CREATE TABLE platform_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_email VARCHAR(150) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(64),
    builder_id  UUID REFERENCES builders(id),
    details     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_platform_audit_created ON platform_audit_log (created_at DESC);
CREATE INDEX idx_platform_audit_builder ON platform_audit_log (builder_id);

CREATE TABLE platform_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO platform_settings (setting_key, setting_value) VALUES
    ('default_vault_enabled', 'true'),
    ('default_expenses_enabled', 'true'),
    ('default_receipt_prefix', 'RCP'),
    ('support_email', 'support@floor21.com')
ON CONFLICT (setting_key) DO NOTHING;
