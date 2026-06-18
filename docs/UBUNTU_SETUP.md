# Floor21 — Ubuntu server setup

Step-by-step setup for Ubuntu (VM or bare metal): JDK 26, Docker, PostgreSQL via Compose, clone the app, run Spring Boot in the background.

---

## 1. System update

```bash
sudo apt update
sudo apt upgrade -y
```

---

## 2. JDK 26 (Oracle)

Download page (pick the build that matches your CPU):

https://www.oracle.com/java/technologies/downloads/

### Check architecture (VM / CPU)

```bash
uname -m
```

| `uname -m`   | Debian package to use        |
|--------------|--------------------------------|
| `x86_64`     | `jdk-26_linux-x64_bin.deb` |
| `aarch64`    | Use the **linux-aarch64** `.deb` from Oracle (not the x64 link below) |

### Download and install (x86_64 example)

```bash
cd /tmp
wget https://download.oracle.com/java/26/latest/jdk-26_linux-x64_bin.deb
sudo apt install ./jdk-26_linux-x64_bin.deb
```

### Verify

```bash
java --version
```

Set `JAVA_HOME` if needed (adjust path if your install differs):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-26-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
```

Add those lines to `~/.bashrc` for a permanent shell setup.

---

## 3. Docker and Compose

```bash
sudo apt install -y docker.io docker-compose
sudo systemctl enable --now docker
```

Optional — run Docker without `sudo` (log out and back in after):

```bash
sudo usermod -aG docker "$USER"
```

---

## 4. Clone the application

```bash
cd ~
git clone https://github.com/913165/floor21_V1.git
cd floor21_V1
```

If you use a different branch or fork, `cd` into that directory for the steps below.

---

## 5. PostgreSQL (Docker)

From the project root:

```bash
docker compose up -d postgres
```

Check the container:

```bash
docker compose ps
docker compose logs postgres
```

Default DB (see `docker-compose.yml`):

| Setting  | Value          |
|----------|----------------|
| Database | `floor21_db`   |
| User     | `floor21_user` |
| Password | `floor21_pass` |
| Port     | `5432`         |

App datasource in `application.yml` should point at this host (`localhost:5432`) when Spring Boot runs on the same machine.

**Flyway:** the repo ships one migration file, `V1__baseline.sql`. On a **new** database the app creates the schema on first start. If this server still has the old `V1`–`V50` Flyway history, back up first, then drop/recreate the database or follow [DB_MIGRATIONS.md](DB_MIGRATIONS.md).

After `git pull`, always build with **`./mvnw clean package`** (not `package` alone) so `target/classes/db/migration/` does not keep deleted SQL files. See [DB_MIGRATIONS.md](DB_MIGRATIONS.md) if startup reports duplicate version 1 or checksum mismatch.

---

## 6. Networks, tags, and firewall

Configure these for your environment (cloud provider console or `ufw` on the VM).

**Suggested:**

- Allow **22** (SSH) only from trusted IPs.
- Allow **80** (HTTP) only if the app must be reached from outside (otherwise restrict to VPN / internal network).
- **5432** — do not expose PostgreSQL to the public internet; keep it on the Docker bridge or localhost only.
- Optional monitoring (if you start Prometheus/Grafana): **9090**, **3000** — internal/VPN only in production.

Example (`ufw`):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp comment 'Floor21 app HTTP'
sudo ufw enable
sudo ufw status
```

Use cloud **security groups / network tags** the same way: least privilege, no open Postgres from `0.0.0.0/0`.

---

## 7. Run Spring Boot in the background

Port **80** is a privileged port on Linux. Allow the JDK to bind to it once (adjust the `java` path if yours differs):

```bash
sudo setcap 'cap_net_bind_service=+ep' "$(readlink -f "$(which java)")"
```

From the project root, with Postgres already up:

```bash
chmod +x mvnw
mkdir -p logs
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &
tail -f logs/floor21.log
```

Application logs go to **`logs/floor21.log`** with Log4j2 rolling (10 MB per file, up to 5 archived files). Redirecting Maven output to `app.log` is no longer needed.

Stop following the log with `Ctrl+C` (the app keeps running in the background).

**URLs** (context path `/floor21`):

| What  | URL |
|-------|-----|
| Login | http://\<server-ip\>/floor21/login |
| Health | http://localhost/floor21/actuator/health |

---

## 8. Stop the app (free port 80)

Install `lsof` if missing:

```bash
sudo apt install -y lsof
```

Kill whatever is listening on **80**:

```bash
lsof -iTCP:80 -sTCP:LISTEN
sudo kill -9 $(sudo lsof -t -i:80)
```

Or find the `nohup` / Java PID:

```bash
ps aux | grep floor21
kill <pid>
```

---

## 9. Optional — monitoring containers

See [MONITORING.md](MONITORING.md):

```bash
docker compose up prometheus -d
docker compose up grafana -d
```

Run the app on the host (section 7) so Prometheus can scrape `host.docker.internal:80`.

---

## Quick reference

```bash
sudo apt update && sudo apt upgrade -y
# JDK 26 → java --version
sudo apt install -y docker.io docker-compose
git clone https://github.com/913165/floor21_V1.git && cd floor21_V1
docker compose up -d postgres
mkdir -p logs
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &
tail -f logs/floor21.log
```
