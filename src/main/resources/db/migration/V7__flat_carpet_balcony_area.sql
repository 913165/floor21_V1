ALTER TABLE flats
    ADD COLUMN carpet_area_sqft DECIMAL(10, 2),
    ADD COLUMN balcony_area_sqft DECIMAL(10, 2),
    ADD COLUMN pre_merge_carpet_area_sqft DECIMAL(10, 2),
    ADD COLUMN pre_merge_balcony_area_sqft DECIMAL(10, 2);
