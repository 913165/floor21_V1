-- Run on floor21_db when Flyway reports checksum mismatch or missing migrations after
-- consolidating to V1__baseline.sql (schema must already be up to date).

-- Option A — align history to the single baseline file (keeps your data):
DELETE FROM flyway_schema_history;

INSERT INTO flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
) VALUES (
    1,
    '1',
    'baseline',
    'SQL',
    'V1__baseline.sql',
    -1473776796,
    current_user,
    now(),
    0,
    true
);

-- Option B — empty database only (destroys all data):
-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public;
-- GRANT ALL ON SCHEMA public TO floor21_user;
-- GRANT ALL ON SCHEMA public TO public;
-- Then restart the app (Flyway runs V1__baseline.sql).
