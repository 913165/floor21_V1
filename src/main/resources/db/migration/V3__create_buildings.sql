CREATE TABLE buildings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id          UUID NOT NULL REFERENCES builders(id),
    building_name       VARCHAR(200) NOT NULL,
    total_floors        INT NOT NULL,
    parking_floors      INT DEFAULT 0,
    flats_per_floor     INT NOT NULL,
    bhk1_per_floor      INT DEFAULT 0,
    bhk2_per_floor      INT DEFAULT 0,
    bhk3_per_floor      INT DEFAULT 0,
    address             TEXT,
    city                VARCHAR(100),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT now()
);
