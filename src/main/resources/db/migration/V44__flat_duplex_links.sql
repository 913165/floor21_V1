ALTER TABLE flats
    ADD COLUMN duplex_primary_flat_id UUID REFERENCES flats (id),
    ADD COLUMN duplex_secondary_flat_id UUID REFERENCES flats (id);

CREATE INDEX idx_flats_duplex_primary ON flats (duplex_primary_flat_id)
    WHERE duplex_primary_flat_id IS NOT NULL;

CREATE INDEX idx_flats_duplex_secondary ON flats (duplex_secondary_flat_id)
    WHERE duplex_secondary_flat_id IS NOT NULL;
