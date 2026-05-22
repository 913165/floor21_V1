# Floor21 — Monitoring (Docker + CLI)

Run each container with its own command. Run Spring Boot on the host (not in Docker).

From the project root (`floor21/`).

---

## 1. PostgreSQL

```powershell
docker compose up postgres -d
```

| | |
|---|---|
| Container | `floor21_postgres` |
| Port | `5432` |

Stop:

```powershell
docker compose stop postgres
```

---

## 2. Prometheus

Start **after** Postgres if the app will use the DB. Prometheus scrapes the app on your machine (`host.docker.internal:8080`).

```powershell
docker compose up prometheus -d
```

| | |
|---|---|
| Container | `floor21_prometheus` |
| UI | http://localhost:9090 |
| Scrape target | `host.docker.internal:8080` → `/floor21/actuator/prometheus` |

Stop:

```powershell
docker compose stop prometheus
```

---

## 3. Grafana

```powershell
docker compose up grafana -d
```

(Compose will also start **prometheus** if it is not already running, because Grafana depends on it.)

| | |
|---|---|
| Container | `floor21_grafana` |
| UI | http://localhost:3000 |
| Login | `admin` / `floor21` |
| Dashboard | **Dashboards → Floor21 → Floor21 — Spring Boot overview** |

Stop:

```powershell
docker compose stop grafana
```

---

## 4. Spring Boot (host — not Docker)

```powershell
.\mvnw.cmd spring-boot:run
```

| | |
|---|---|
| App | http://localhost:8080/floor21 |
| Metrics | http://localhost:8080/floor21/actuator/prometheus |
| Health | http://localhost:8080/floor21/actuator/health |

Start the app **before** checking Prometheus targets or Grafana charts.

---

## Typical order

```powershell
docker compose up postgres -d
docker compose up prometheus -d
docker compose up grafana -d
.\mvnw.cmd spring-boot:run
```

---

## Stop everything

```powershell
docker compose stop grafana prometheus postgres
```

---

## Optional — app in Docker instead of CLI

Uses `docker-compose.app.yml` and scrapes `floor21-app:8080`:

```powershell
docker compose up postgres -d
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build floor21-app prometheus grafana -d
```

---

## URLs (quick reference)

| What | URL |
|------|-----|
| App | http://localhost:8080/floor21 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| Metrics | http://localhost:8080/floor21/actuator/prometheus |

Change Grafana password in production: `GF_SECURITY_ADMIN_PASSWORD` in `docker-compose.yml`.
