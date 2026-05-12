CREATE TABLE slabs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID REFERENCES buildings(id),
    slab_name       VARCHAR(100),
    description     TEXT,
    rate_per_sqft   DECIMAL(10,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);
