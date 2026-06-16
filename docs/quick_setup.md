Here's the **updated minimal Ubuntu server setup** keeping only:

* PostgreSQL via Docker Compose
* Find/Kill process on port 80
* Run Spring Boot in background with `nohup`
* View logs

---

# Floor21 — Minimal Ubuntu Runtime Setup

## 1. PostgreSQL (Docker)

From the project root:

```bash
docker compose up -d postgres
```



## 2. Find process using port 80

See what is listening on port 80:

```bash
sudo lsof -iTCP:80 -sTCP:LISTEN
```

Example output:

```bash
COMMAND   PID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
java    12345 ubuntu  50u  IPv6 ...    TCP *:http (LISTEN)
```

---

## 3. Kill process on port 80

Kill whatever is occupying port 80:

```bash
sudo kill -9 $(sudo lsof -t -i:80)
```

Verify:

```bash
sudo lsof -iTCP:80 -sTCP:LISTEN
```

No output means port 80 is free.

---

## 4. Find Floor21 Java process

```bash
ps aux | grep floor21
```

Or:

```bash
ps -ef | grep java
```

---

## 5. Run Floor21 in background

From the project root:

```bash
chmod +x mvnw
mkdir -p logs

nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &
```



## Quick Commands

```bash
# Start PostgreSQL
docker compose up -d postgres

# Kill app on port 80
sudo kill -9 $(sudo lsof -t -i:80)

# Start Floor21
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &

# Watch logs
tail -f logs/floor21.log
```
