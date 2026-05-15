CREATE TABLE banks (
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    builder_id          UUID        NOT NULL REFERENCES builders (id),
    bank_name           VARCHAR(200) NOT NULL,
    branch              VARCHAR(200),
    ifsc_code           VARCHAR(20),
    account_number      VARCHAR(64),
    account_holder_name VARCHAR(200),
    notes                 TEXT,
    active                BOOLEAN     NOT NULL DEFAULT true,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_banks_builder ON banks (builder_id);
