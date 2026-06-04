ALTER TABLE flats
    ADD COLUMN IF NOT EXISTS linked_residential_flat_id UUID REFERENCES flats (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_flats_linked_residential_flat_id ON flats (linked_residential_flat_id);
