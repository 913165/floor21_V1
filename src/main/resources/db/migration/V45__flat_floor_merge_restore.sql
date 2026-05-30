ALTER TABLE flats
    ADD COLUMN merged_into_flat_id UUID REFERENCES flats(id),
    ADD COLUMN merged_absorbed_flat_id UUID REFERENCES flats(id),
    ADD COLUMN pre_merge_bhk_type VARCHAR(20),
    ADD COLUMN pre_merge_area_sqft DECIMAL(10, 2),
    ADD COLUMN pre_merge_base_price DECIMAL(15, 2),
    ADD COLUMN pre_merge_status VARCHAR(20);

CREATE INDEX idx_flats_merged_into ON flats (merged_into_flat_id)
    WHERE merged_into_flat_id IS NOT NULL;
