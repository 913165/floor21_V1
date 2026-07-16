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
- Allow **443** (HTTPS) for the Floor21 app (or **80** only if you run without the `ssl` profile).
- **5432** — do not expose PostgreSQL to the public internet; keep it on the Docker bridge or localhost only.
- Optional monitoring (if you start Prometheus/Grafana): **9090**, **3000** — internal/VPN only in production.

Example (`ufw`):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 443/tcp comment 'Floor21 app HTTPS'
sudo ufw enable
sudo ufw status
```

Use cloud **security groups / network tags** the same way: least privilege, no open Postgres from `0.0.0.0/0`.

---

## 7. Run Spring Boot in the background

Port **443** (HTTPS) is a privileged port on Linux. Allow the JDK to bind to it once (adjust the `java` path if yours differs):

```bash
sudo setcap 'cap_net_bind_service=+ep' "$(readlink -f "$(which java)")"
```

From the project root, with Postgres already up:

```bash
chmod +x mvnw
mkdir -p logs
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod,ssl -q > /dev/null 2>&1 &
tail -f logs/floor21.log
```

Application logs go to **`logs/floor21.log`** with Log4j2 rolling (10 MB per file, up to 5 archived files). Redirecting Maven output to `app.log` is no longer needed.

Stop following the log with `Ctrl+C` (the app keeps running in the background).

**URLs** (context path `/floor21`):

| What  | URL |
|-------|-----|
| Login | https://floor21.in/floor21/login |
| Health | https://localhost/floor21/actuator/health |

---

## 7a. HTTPS with PKCS12 keystore (`floor21.p12`) — floor21.in

The app uses **`/home/tinumistry/floor21.p12`** (PKCS12). Do **not** mix this with PEM paths (`fullchain.pem` / `privkey.pem`) in the same profile.

**Verify the keystore exists:**

```bash
ls -l /home/tinumistry/floor21.p12
```

If missing, create it from your Let's Encrypt cert (one-time):

```bash
sudo openssl pkcs12 -export \
  -in /etc/letsencrypt/live/floor21.in/fullchain.pem \
  -inkey /etc/letsencrypt/live/floor21.in/privkey.pem \
  -out /home/tinumistry/floor21.p12 \
  -name floor21 \
  -passout pass:'Varsha1#'
sudo chown tinumistry:tinumistry /home/tinumistry/floor21.p12
chmod 600 /home/tinumistry/floor21.p12
```

**Stop the old HTTP-only process** (if still running on port 80):

```bash
sudo lsof -iTCP:80 -sTCP:LISTEN
sudo kill -9 $(sudo lsof -t -i:80)   # only if Floor21 is on 80
```

**Start with HTTPS** (`prod` + `ssl` profiles):

```bash
cd ~/floor21_V1   # or your clone path
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod,ssl -q > /dev/null 2>&1 &
tail -f logs/floor21.log
```

Confirm HTTPS is listening:

```bash
sudo lsof -iTCP:443 -sTCP:LISTEN
curl -k https://floor21.in/floor21/actuator/health
```

**After Certbot renews** (re-export `.p12` or use Nginx in section 7b):

```bash
sudo openssl pkcs12 -export \
  -in /etc/letsencrypt/live/floor21.in/fullchain.pem \
  -inkey /etc/letsencrypt/live/floor21.in/privkey.pem \
  -out /home/tinumistry/floor21.p12 \
  -name floor21 \
  -passout pass:'Varsha1#'
```

### Troubleshooting — still only HTTP on port 80?

| Symptom | Cause | Fix |
|---------|-------|-----|
| App on port 80 | Started with `prod` only, not `prod,ssl` | Restart with `-Dspring-boot.run.profiles=prod,ssl` |
| `fullchain.pem` / `privkey.pem` not found | Wrong SSL mode — app expects `.p12` | Use `floor21.p12` (see above), not PEM paths |
| `floor21.p12` not found | Keystore missing | Run `ls -l` or create with `openssl pkcs12` |
| Port 443 permission denied | Java cannot bind to 443 | Run `setcap` (section 7) |
| Wrong password | `SSL_KEY_STORE_PASSWORD` mismatch | Export with same password as in config, or set env var |

---

## 7b. Alternative — Nginx terminates HTTPS (common with Certbot)

If you ran `sudo certbot --nginx`, Nginx may already have HTTPS on 443 while Floor21 still runs on HTTP port 80. That is normal — Nginx should proxy to the app.

1. Run Floor21 **without** the `ssl` profile (HTTP on 80):

```bash
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &
```

2. Ensure Nginx proxies `/floor21` to the app (example site config):

```nginx
server {
    listen 443 ssl;
    server_name floor21.in www.floor21.in;

    ssl_certificate     /etc/letsencrypt/live/floor21.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/floor21.in/privkey.pem;

    location /floor21 {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

server {
    listen 80;
    server_name floor21.in www.floor21.in;
    return 301 https://$host$request_uri;
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Users then open **https://floor21.in/floor21/login** — Nginx handles SSL; Spring Boot stays on HTTP internally.

---

## 8. Stop the app (free ports 80 / 443)

Install `lsof` if missing:

```bash
sudo apt install -y lsof
```

Kill whatever is listening on **80** or **443**:

```bash
lsof -iTCP:80 -sTCP:LISTEN
lsof -iTCP:443 -sTCP:LISTEN
sudo kill -9 $(sudo lsof -t -i:80)    # if needed
sudo kill -9 $(sudo lsof -t -i:443)   # if needed
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
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod,ssl -q > /dev/null 2>&1 &
tail -f logs/floor21.log
```
