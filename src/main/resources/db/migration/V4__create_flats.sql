CREATE TABLE flats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID NOT NULL REFERENCES buildings(id),
    flat_number     VARCHAR(20) NOT NULL,
    floor_number    INT NOT NULL,
    unit_number     INT NOT NULL,
    bhk_type        VARCHAR(10) NOT NULL,
    area_sqft       DECIMAL(10,2),
    base_price      DECIMAL(15,2),
    status          VARCHAR(20) DEFAULT 'AVAILABLE',
    is_parking      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT now(),
    UNIQUE(building_id, flat_number)
);
