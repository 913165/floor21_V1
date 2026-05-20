-- Last password set via platform admin (Users screen); for display when editing only.
ALTER TABLE users ADD COLUMN IF NOT EXISTS admin_visible_password VARCHAR(255);
