CREATE TABLE brokers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    full_name       VARCHAR(200) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(150),
    commission_pct  DECIMAL(5,2) DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);
