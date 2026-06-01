# Database migrations (Flyway)

The app uses a **single** Flyway script:

- `src/main/resources/db/migration/V1__baseline.sql` — full schema, alters, and dev seed data (replaces the old `V1`–`V50` chain).

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
| Builder admin | `admin@skylinehomes.com` | `admin123` |
| Executive | `exec@skylinehomes.com` | `exec123` |

Passwords use Spring Security `{noop}` encoding in seed SQL.
