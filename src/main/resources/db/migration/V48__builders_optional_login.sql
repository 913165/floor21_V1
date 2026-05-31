-- Projects (builders) no longer require a login on the tenant row; users are added separately.
ALTER TABLE builders ALTER COLUMN email DROP NOT NULL;
ALTER TABLE builders ALTER COLUMN password_hash DROP NOT NULL;
