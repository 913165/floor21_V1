# Database migrations (Flyway)

The app uses a **single** Flyway script:

- `src/main/resources/db/migration/V1__baseline.sql` — full schema, alters, and dev seed data (replaces the old `V1`–`V50` chain).
- `src/main/resources/db/migration/V2__users_company_name.sql` — adds `users.company_name` for User Management.

## New install (empty database)

1. Create an empty PostgreSQL database (e.g. `floor21_db`).
2. Start the app — Flyway runs `V1__baseline.sql` once.

With Docker Compose from the repo root:

```bash
docker compose up -d
# then start the Spring Boot app
```

## “Found more than one migration with version 1”

Flyway sees both `V1__create_builders.sql` and `V1__baseline.sql` (usually under `target/classes/db/migration/`).

**On the server:**

```bash
cd /home/tinumistry/floor21_V1   # your deploy path
git pull

# Source must contain ONLY the baseline file:
ls src/main/resources/db/migration/
# Expected: V1__baseline.sql

# Remove leftover old migrations if present (keep V1__baseline.sql):
find src/main/resources/db/migration -name 'V*.sql' ! -name 'V1__baseline.sql' -delete

# Rebuild without stale target/ classes:
./mvnw clean package -DskipTests

ls target/classes/db/migration/
# Expected: V1__baseline.sql only

# Restart the app service
```

Always run **`mvn clean`** (or `./mvnw clean package`) after pulling migration changes. A plain `package` can leave old SQL files in `target/classes`.

## `missing column [company_name] in table [users]`

Hibernate validates the schema before Flyway has applied `V2__users_company_name.sql`.

1. Rebuild so `V2__users_company_name.sql` is on the classpath:

   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Restart the app (Flyway should apply version 2 on startup).

**If startup still fails**, apply the column once in PostgreSQL:

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(200);
INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
) VALUES (
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '2', 'users company name', 'SQL', 'V2__users_company_name.sql', 0, 'manual', NOW(), 0, TRUE
) ON CONFLICT DO NOTHING;
```

(If version 2 is already in `flyway_schema_history`, only run the `ALTER TABLE` line.)

## `missing column [updated_at] in table [buildings]`

Same class of issue as `company_name` / `parking_floor_config`: prod DB predates `V4__buildings_updated_at.sql`, or Flyway history is ahead of the real schema.

**Quick fix on the server:**

```sql
ALTER TABLE buildings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
UPDATE buildings SET updated_at = COALESCE(created_at, NOW()) WHERE updated_at IS NULL;
ALTER TABLE buildings ALTER COLUMN updated_at SET DEFAULT NOW();
UPDATE buildings SET updated_at = NOW() WHERE updated_at IS NULL;
ALTER TABLE buildings ALTER COLUMN updated_at SET NOT NULL;
```

Then rebuild/restart after pulling the latest code (startup runs the same steps before Hibernate validate).

## `missing column [parking_floor_config] in table [buildings]`

Hibernate validates before Flyway has applied `V5__parking_floor_config.sql`, or the DB history lists V5/V6 as applied without the column (common after deploys from an older branch).

1. Rebuild and redeploy so `V5` and `V6` are on the classpath:

   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Restart the app. `FlywayDataSourceMigrationConfig` runs an idempotent `ALTER TABLE ... IF NOT EXISTS` before JPA validates.

**If startup still fails**, apply once in PostgreSQL on the server:

```sql
ALTER TABLE buildings ADD COLUMN IF NOT EXISTS parking_floor_config TEXT;
ALTER TABLE flats ADD COLUMN IF NOT EXISTS linked_residential_flat_id UUID;
```

If `linked_residential_flat_id` needs the foreign key (optional if the app starts after the column exists):

```sql
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'flats_linked_residential_flat_id_fkey'
  ) THEN
    ALTER TABLE flats
      ADD CONSTRAINT flats_linked_residential_flat_id_fkey
      FOREIGN KEY (linked_residential_flat_id) REFERENCES flats (id) ON DELETE SET NULL;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_flats_linked_residential_flat_id ON flats (linked_residential_flat_id);
```

Check pending Flyway versions:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

If V5 is missing from history but the column exists, do not insert a fake row unless you know Flyway checksum rules; prefer `flyway repair` + restart after a clean deploy.

## Flyway checksum mismatch on startup

Error example:

```text
Migration checksum mismatch for migration version 1
-> Applied to database : -1942116334
-> Resolved locally    : -1473776796
```

**Dev (default profile):** restart the app — `DevFlywayConfig` runs `flyway.repair()` then `migrate()`, and `ignore-missing-migrations` is enabled in `application-dev.yml`.

**Manual (any environment):** run [scripts/fix-flyway-after-baseline-squash.sql](scripts/fix-flyway-after-baseline-squash.sql) Option A in `psql`, then restart.

**Clean slate (Docker, no data to keep):**

```bash
docker compose down -v
docker compose up -d postgres
```

Then start the app.

## Local dev — reset after pulling this change

If repair does not help, or you prefer a clean database:

```sql
-- Connect as a superuser, then:
DROP DATABASE floor21_db;
CREATE DATABASE floor21_db OWNER floor21_user;
```

Or drop only the schema and Flyway table:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO floor21_user;
```

Then restart the application.

## Existing server (already on V50)

Do **not** only replace migration files on a database that already applied `V1`–`V50` unless you plan a controlled cutover:

1. **Backup** the database first.
2. Either keep the old migration files on that environment until the next full DB rebuild, **or**
3. After backup, recreate the database from scratch and restore data with `pg_dump` / `pg_restore` (schema + data), then align `flyway_schema_history` to a single version `1` entry for `V1__baseline.sql` (advanced — only if you know Flyway checksum rules).

For most teams: **new environments** use `V1__baseline.sql`; **production** stays on the old migration set until a planned maintenance window with backup + recreate or a DBA-led Flyway repair.

## Seed users (from baseline)

| Role | Email | Password |
|------|--------|----------|
| Super admin | `super@floor21.com` | `super123` |

Migration `V3__remove_demo_seed_data.sql` removes the old demo tenant projects (Skyline, Green Valley, Sunrise) and related seed rows on startup. Only the platform super admin remains from seed data.

Passwords use Spring Security `{noop}` encoding in seed SQL.
