-- Partner / user business name (shown in User Management).
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(200);
