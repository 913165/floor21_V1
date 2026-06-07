ALTER TABLE buildings ADD COLUMN IF NOT EXISTS ground_floor_shop_count INT DEFAULT 0;
ALTER TABLE buildings ADD COLUMN IF NOT EXISTS ground_floor_shop_area_sqft DECIMAL(10, 2);
ALTER TABLE buildings ADD COLUMN IF NOT EXISTS ground_floor_config TEXT;
