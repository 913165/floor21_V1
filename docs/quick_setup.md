
# Floor21 — Minimal Ubuntu Runtime Setup

## 1. PostgreSQL (Docker)

From the project root:

```bash
docker compose up -d postgres
```



## 2. Find process using port 80

See what is listening on port 80:

```bash
lsof -iTCP:80 -sTCP:LISTEN
```

## 3. Kill process on port 80

Kill whatever is occupying port 80:

```bash
sudo kill -9 $(sudo lsof -t -i:80)
```

# chmod command

```
chmod -R 777 *
```


# Start Floor21

```bash
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod -q > /dev/null 2>&1 &
```
# Watch logs
```bash
tail -f logs/floor21.log
```
