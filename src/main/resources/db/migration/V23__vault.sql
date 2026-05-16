ALTER TABLE builders
    ADD COLUMN vault_pin_hash VARCHAR(255);

CREATE TABLE vault_entries (
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    builder_id   UUID        NOT NULL REFERENCES builders (id),
    client_name  VARCHAR(200) NOT NULL,
    amount       NUMERIC(15, 2) NOT NULL,
    entry_date   DATE        NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT vault_entries_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_vault_entries_builder_date ON vault_entries (builder_id, entry_date DESC);
