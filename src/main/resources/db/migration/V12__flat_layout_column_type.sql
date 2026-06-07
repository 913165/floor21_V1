ALTER TABLE flats ADD COLUMN IF NOT EXISTS layout_column_type VARCHAR(10);

ALTER TABLE buildings ADD COLUMN IF NOT EXISTS column_type_defaults TEXT;
