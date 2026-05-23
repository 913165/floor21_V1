# Floor21 — Ubuntu server setup

Step-by-step setup for Ubuntu (VM or bare metal): JDK 25, Docker, PostgreSQL via Compose, clone the app, run Spring Boot in the background.

---

## 1. System update

```bash
sudo apt update
sudo apt upgrade -y
```

---

## 2. JDK 25 (Oracle)

Download page (pick the build that matches your CPU):

https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html

### Check architecture (VM / CPU)

```bash
uname -m
```

| `uname -m`   | Debian package to use        |
|--------------|--------------------------------|
| `x86_64`     | `jdk-25.0.2_linux-x64_bin.deb` |
| `aarch64`    | Use the **linux-aarch64** `.deb` from Oracle (not the x64 link below) |

### Download and install (x86_64 example)

```bash
cd /tmp
wget https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-x64_bin.deb
sudo apt install ./jdk-25.0.2_linux-x64_bin.deb
```

### Verify

```bash
java --version
```

Set `JAVA_HOME` if needed (adjust path if your install differs):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-25.0.2
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

---

## 6. Networks, tags, and firewall

Configure these for your environment (cloud provider console or `ufw` on the VM).

**Suggested:**

- Allow **22** (SSH) only from trusted IPs.
- Allow **8080** only if the app must be reached from outside (otherwise restrict to VPN / internal network).
- **5432** — do not expose PostgreSQL to the public internet; keep it on the Docker bridge or localhost only.
- Optional monitoring (if you start Prometheus/Grafana): **9090**, **3000** — internal/VPN only in production.

Example (`ufw`):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 8080/tcp comment 'Floor21 app'
sudo ufw enable
sudo ufw status
```

Use cloud **security groups / network tags** the same way: least privilege, no open Postgres from `0.0.0.0/0`.

---

## 7. Run Spring Boot in the background

From the project root, with Postgres already up:

```bash
chmod +x mvnw
nohup ./mvnw spring-boot:run > app.log 2>&1 &
tail -f app.log
```

Stop following the log with `Ctrl+C` (the app keeps running in the background).

**URLs** (context path `/floor21`):

| What  | URL |
|-------|-----|
| Login | http://\<server-ip\>:8080/floor21/login |
| Health | http://localhost:8080/floor21/actuator/health |

---

## 8. Stop the app (free port 8080)

Install `lsof` if missing:

```bash
sudo apt install -y lsof
```

Kill whatever is listening on **8080**:

```bash
kill -9 $(lsof -t -i:8080)
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

Run the app on the host (section 7) so Prometheus can scrape `host.docker.internal:8080`.

---

## Quick reference

```bash
sudo apt update && sudo apt upgrade -y
# JDK 25 → java --version
sudo apt install -y docker.io docker-compose
git clone https://github.com/913165/floor21_V1.git && cd floor21_V1
docker compose up -d postgres
nohup ./mvnw spring-boot:run > app.log 2>&1 & tail -f app.log
```
