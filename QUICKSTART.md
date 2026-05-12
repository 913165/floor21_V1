# Floor21 — Quick start

How to run the app locally and sign in.

## Prerequisites

- **JDK 25** (matches the project `pom.xml`)
- **PostgreSQL 16** with a database the app can use (or Docker — see below)
- **Maven Wrapper** (`mvnw` / `mvnw.cmd`) — already in the repo

Default datasource (see `src/main/resources/application.yml`):

| Setting   | Value        |
|----------|--------------|
| Host     | `localhost`  |
| Port     | `5432`       |
| Database | `floor21_db` |
| User     | `floor21_user` |
| Password | `floor21_pass` |

Create the database and user in PostgreSQL if they do not exist, or use Docker.

## 1. Start PostgreSQL (pick one)

### Option A — Docker (database only)

From the project root:

```bash
docker compose up postgres -d
```

Wait until Postgres is healthy (a few seconds). The app runs on your machine and talks to `localhost:5432`.

### Option B — Docker (app + database)

Requires a built JAR first (`.\mvnw.cmd package -DskipTests`), then:

```bash
docker compose up --build
```

The app listens on port **8080** inside the container.

### Option C — Your own PostgreSQL

Create `floor21_db` and user `floor21_user` / `floor21_pass`, or change `spring.datasource.*` in `application.yml` / env vars.

## 2. Run the application (local JVM)

**Windows (PowerShell)** — set `JAVA_HOME`, then start:

```powershell
cd C:\work_floor21\floor21
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
.\mvnw.cmd spring-boot:run
```

Adjust `JAVA_HOME` if your JDK is installed elsewhere.

**macOS / Linux:**

```bash
export JAVA_HOME=/path/to/jdk-25
./mvnw spring-boot:run
```

On first startup, **Flyway** applies migrations and **seed data** (demo users).

## 3. Open the app and log in

1. In a browser go to: **http://localhost:8080/floor21/login**  
   (context path is **`/floor21`**.)

2. Sign in with **email** and **password** (Spring Security uses the field name `username` for email on the login form — the page is already wired that way).

### Demo accounts (from seed migration)

| Role | Email | Password |
|------|--------|----------|
| Super admin (platform) | `super@floor21.com` | `super123` |
| Builder (Skyline tenant) | `admin@skylinehomes.com` | `admin123` |
| Builder staff — admin | `staff.admin@skylinehomes.com` | `staff123` |
| Builder staff — executive | `exec@skylinehomes.com` | `exec123` |

- **Super admin** can use **Admin → Builders** and the dashboard; tenant pages are restricted.
- **Builder / staff** users get the full tenant UI (buildings, clients, bookings, etc.) for their builder.

After login you land on **http://localhost:8080/floor21/dashboard**.

## 4. Sign out

Use **Logout** in the navbar, or open **http://localhost:8080/floor21/logout** (POST from the UI; the navbar submits the logout form).

## Troubleshooting (short)

| Issue | What to try |
|--------|-------------|
| `JAVA_HOME not found` (Windows) | Use `$env:JAVA_HOME = "..."` in PowerShell, not `set ...`. |
| DB connection / timezone errors | JDK on Windows may use `Asia/Calcutta`; the app normalizes to `Asia/Kolkata` at startup. Ensure Postgres is reachable. |
| Schema validation / missing tables | Use `spring-boot-starter-flyway` (already in `pom.xml`). With a dirty DB, drop/recreate schema or DB and start again. |
| Port 8080 in use | Change `server.port` in `application.yml` or stop the other process. |

For full stack details, see `Floor21_SpringBoot_Thymeleaf_Prompt.md`.
