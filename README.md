# Floor21

Multi-tenant flat booking for builders: buildings, flat grid, clients, bookings,
receipts, brokers, slabs, and cancellations.

Server-rendered with Spring MVC and Thymeleaf (form login, sessions).

## Stack

- Java 26
- Spring Boot 4
- PostgreSQL 16
- Flyway
- Bootstrap 5

App context path: `/floor21`  
Login URL: `http://localhost:8080/floor21/login`

## Quick Start

See [QUICKSTART.md](QUICKSTART.md).

Typical local flow:

1. Start Postgres:
   - `docker compose up postgres -d`
2. Set `JAVA_HOME` to JDK 26
3. Run app:
   - macOS/Linux: `./mvnw spring-boot:run`
   - Windows: `mvnw.cmd spring-boot:run`

## Build

- Verify: `./mvnw clean verify`
- Package: `./mvnw package -DskipTests`
- Run jar: `java -jar target/floor21-1.0.0-SNAPSHOT.jar`

## Configuration

- Main config: `src/main/resources/application.yml`
- Dev config: `src/main/resources/application-dev.yml`
- Product notes: [Floor21_SpringBoot_Thymeleaf_Prompt.md](Floor21_SpringBoot_Thymeleaf_Prompt.md)

## Docker

- Full stack (Postgres + Prometheus on 9090 + Grafana on 3000):
  - `docker compose up -d`
- Postgres only:
  - `docker compose up postgres -d`
- Optional app container:
  - `docker compose -f docker-compose.yml -f docker-compose.app.yml up --build`

Monitoring docs: [docs/MONITORING.md](docs/MONITORING.md)

## Project Layout

- Java: `src/main/java/com/floor21/`
- Thymeleaf templates: `src/main/resources/templates/`
- Flyway SQL: `src/main/resources/db/migration/`

Add a license before publishing the repository.

