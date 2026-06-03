ALTER TABLE flats
    ADD COLUMN linked_residential_flat_id UUID REFERENCES flats (id) ON DELETE SET NULL;

CREATE INDEX idx_flats_linked_residential_flat_id ON flats (linked_residential_flat_id);
