-- Second platform super admin (same password as super@floor21.com).
-- Idempotent: safe on fresh DB and on DBs that already ran V1 baseline.

INSERT INTO builders (company_name, email, password_hash, city, is_platform_admin, is_active)
SELECT 'Floor21 Platform Admin 2', 'super2@floor21.in', '{noop}super123', 'System', TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM builders WHERE lower(email) = lower('super2@floor21.in')
);
