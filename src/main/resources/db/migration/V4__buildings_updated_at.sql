-- Track last change time on buildings (matches builders / clients pattern).
ALTER TABLE buildings
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE buildings
SET updated_at = COALESCE(created_at, NOW())
WHERE updated_at IS NULL;

ALTER TABLE buildings
    ALTER COLUMN updated_at SET DEFAULT NOW();

UPDATE buildings
SET updated_at = NOW()
WHERE updated_at IS NULL;

ALTER TABLE buildings
    ALTER COLUMN updated_at SET NOT NULL;
