# Floor21 — Flat Booking Management System
## Spring Boot 4 + Thymeleaf Full Project Prompt

---

## 1. Project Identity

| Field | Value |
|---|---|
| App Name | **Floor21** |
| Tagline | *Smart Flat Booking for Modern Builders* |
| Backend | Spring Boot 4.x + Java 25 |
| Frontend | Thymeleaf 3.1 + Bootstrap 5 + Vanilla JS |
| Database | PostgreSQL 16+ |
| Architecture | Multi-Tenant MVC Web App |
| Build Tool | Maven 3.9+ |
| Context Path | `/floor21` |

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.x |
| Language | Java 25 |
| Database | PostgreSQL 16+ |
| ORM | Spring Data JPA / Hibernate 7 |
| Security | Spring Security 7 (form-based login + session) |
| Template Engine | Thymeleaf 3.1 + Thymeleaf Spring Security extras |
| DB Migration | Flyway |
| Validation | Jakarta Bean Validation 3 |
| Frontend CSS | Bootstrap 5.3 |
| Frontend JS | Vanilla JS (fetch API for flat grid AJAX) |
| Build Tool | Maven 3.9+ |
| Testing | JUnit 5 + Mockito + Testcontainers (PostgreSQL) |
| Containerization | Docker + Docker Compose |

> **No REST API / No JWT.** This is a server-side rendered MVC application.
> Spring Security manages sessions. Thymeleaf renders all pages server-side.
> A small amount of Vanilla JS is used only for the interactive flat booking grid (AJAX calls to internal Spring MVC endpoints that return JSON fragments).

---

## 3. Multi-Tenant Architecture

- The system supports **multiple builders** (tenants). E.g. "Skyline Homes", "Green Valley Developers".
- Each builder logs in with their own email + password via Spring Security form login.
- After login, the logged-in builder's `builderId` (UUID) is stored in the HTTP session and in a `TenantContext` ThreadLocal.
- A Spring MVC `HandlerInterceptor` (`TenantInterceptor`) extracts `builderId` from the session before every controller method and sets it in `TenantContext`.
- Every Spring Data JPA repository query includes `WHERE builder_id = :builderId` automatically — using either method naming or `@Query`.
- **Builders cannot see each other's data under any circumstance.**
- A **Super Admin** role bypasses tenant filtering and can manage all builders.

---

## 4. Database Schema (PostgreSQL + Flyway)

### V1 — `builders`
```sql
CREATE TABLE builders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name    VARCHAR(200) NOT NULL,
    email           VARCHAR(150) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    logo_url        VARCHAR(500),
    address         TEXT,
    city            VARCHAR(100),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);
```

### V2 — `users` (Builder staff: Admin, Executive)
```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL, -- BUILDER_ADMIN, EXECUTIVE
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now(),
    UNIQUE(builder_id, email)
);
```

### V3 — `buildings`
```sql
CREATE TABLE buildings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id          UUID NOT NULL REFERENCES builders(id),
    building_name       VARCHAR(200) NOT NULL,
    total_floors        INT NOT NULL,
    parking_floors      INT DEFAULT 0,
    flats_per_floor     INT NOT NULL,
    bhk1_per_floor      INT DEFAULT 0,
    bhk2_per_floor      INT DEFAULT 0,
    bhk3_per_floor      INT DEFAULT 0,
    address             TEXT,
    city                VARCHAR(100),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT now()
);
```

### V4 — `flats`
```sql
CREATE TABLE flats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID NOT NULL REFERENCES buildings(id),
    flat_number     VARCHAR(20) NOT NULL,   -- e.g. "0101", "1904"
    floor_number    INT NOT NULL,
    unit_number     INT NOT NULL,
    bhk_type        VARCHAR(10) NOT NULL,   -- 1BHK, 2BHK, 3BHK, Penthouse
    area_sqft       DECIMAL(10,2),
    base_price      DECIMAL(15,2),
    status          VARCHAR(20) DEFAULT 'AVAILABLE', -- AVAILABLE, BOOKED, HOLD, CANCELLED
    is_parking      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT now(),
    UNIQUE(building_id, flat_number)
);
```

### V5 — `clients`
```sql
CREATE TABLE clients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id              UUID NOT NULL REFERENCES builders(id),
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100),
    company_name            VARCHAR(200),
    occupation              VARCHAR(100),
    address1                TEXT,
    address2                TEXT,
    address3                TEXT,
    city                    VARCHAR(100),
    phone_office            VARCHAR(20),
    phone_residence         VARCHAR(20),
    mobile1                 VARCHAR(20),
    mobile2                 VARCHAR(20),
    email1                  VARCHAR(150),
    email2                  VARCHAR(150),
    pan_number              VARCHAR(20),
    aadhaar_number          VARCHAR(20),
    dob                     DATE,
    date_of_marriage        DATE,
    dnd_no_call             BOOLEAN DEFAULT FALSE,
    dnd_only_email          BOOLEAN DEFAULT FALSE,
    comm_address1           TEXT,
    comm_address2           TEXT,
    comm_address3           TEXT,
    comm_city               VARCHAR(100),
    name_plate_info         VARCHAR(300),
    particulars             TEXT,
    created_at              TIMESTAMP DEFAULT now(),
    updated_at              TIMESTAMP DEFAULT now()
);
```

### V6 — `brokers`
```sql
CREATE TABLE brokers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    full_name       VARCHAR(200) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(150),
    commission_pct  DECIMAL(5,2) DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);
```

### V7 — `bookings`
```sql
CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id          UUID NOT NULL REFERENCES builders(id),
    booking_code        VARCHAR(30) UNIQUE NOT NULL,
    flat_id             UUID NOT NULL REFERENCES flats(id),
    client_id           UUID NOT NULL REFERENCES clients(id),
    broker_id           UUID REFERENCES brokers(id),
    executive_id        UUID REFERENCES users(id),
    file_no             VARCHAR(50),
    booking_date        DATE NOT NULL,
    consideration_amt   DECIMAL(15,2) DEFAULT 0,
    scheme              VARCHAR(100),
    parking_info        TEXT,
    reference           TEXT,
    is_bare_flat        BOOLEAN DEFAULT FALSE,
    particulars         TEXT,
    status              VARCHAR(20) DEFAULT 'ACTIVE',
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now()
);
```

### V8 — `receipts`
```sql
CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    receipt_date    DATE NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    payment_mode    VARCHAR(50),
    cheque_no       VARCHAR(50),
    bank_name       VARCHAR(100),
    remarks         TEXT,
    created_at      TIMESTAMP DEFAULT now()
);
```

### V9 — `cancellations`
```sql
CREATE TABLE cancellations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    cancel_date     DATE NOT NULL,
    reason          TEXT,
    refund_amount   DECIMAL(15,2) DEFAULT 0,
    created_at      TIMESTAMP DEFAULT now()
);
```

### V10 — `extra_expenses`
```sql
CREATE TABLE extra_expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    description     VARCHAR(300),
    amount          DECIMAL(15,2),
    expense_date    DATE,
    created_at      TIMESTAMP DEFAULT now()
);
```

### V11 — `slabs`
```sql
CREATE TABLE slabs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    builder_id      UUID NOT NULL REFERENCES builders(id),
    building_id     UUID REFERENCES buildings(id),
    slab_name       VARCHAR(100),
    description     TEXT,
    rate_per_sqft   DECIMAL(10,2),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT now()
);
```

---

## 5. User Roles & Permissions

| Role | Description | Access |
|---|---|---|
| `SUPER_ADMIN` | Platform owner | Manage all builders, view all data |
| `BUILDER_ADMIN` | Builder's admin login | Full access to own tenant: buildings, flats, clients, bookings |
| `EXECUTIVE` | Builder's sales staff | Manage clients, bookings, receipts |

---

## 6. Thymeleaf Page Structure & URL Mapping

### Public Pages
```
GET  /floor21/login                   → login.html
GET  /floor21/logout                  → logout (Spring Security)
```

### Dashboard
```
GET  /floor21/dashboard               → dashboard.html
     (shows: total flats, booked, available, revenue, recent bookings)
```

### Buildings
```
GET  /floor21/buildings               → buildings/list.html
GET  /floor21/buildings/new           → buildings/form.html (create)
POST /floor21/buildings/save          → save and redirect
GET  /floor21/buildings/{id}/edit     → buildings/form.html (edit)
GET  /floor21/buildings/{id}/flats    → buildings/flat-grid.html ← THE FLAT BOOKING UI
```

### Flat Grid (the interactive seat-map style page)
```
GET  /floor21/buildings/{id}/flats             → flat-grid.html (Thymeleaf renders initial state)
GET  /floor21/buildings/{id}/flats/data        → returns JSON (for AJAX flat status refresh)
POST /floor21/buildings/{id}/flats/generate    → auto-generate flats based on building config
POST /floor21/flats/{id}/status                → update flat status (HOLD/AVAILABLE)
```

### Clients
```
GET  /floor21/clients                 → clients/list.html
GET  /floor21/clients/new             → clients/form.html
POST /floor21/clients/save            → save and redirect
GET  /floor21/clients/{id}            → clients/detail.html
GET  /floor21/clients/{id}/edit       → clients/form.html (edit)
GET  /floor21/clients/search?q=       → returns clients/list.html fragment (AJAX)
```

### Bookings
```
GET  /floor21/bookings                → bookings/list.html
GET  /floor21/bookings/new            → bookings/form.html (Booking Request Entry)
POST /floor21/bookings/save           → save and redirect
GET  /floor21/bookings/{id}           → bookings/detail.html
GET  /floor21/bookings/{id}/edit      → bookings/form.html (edit)
```

### Receipts
```
GET  /floor21/bookings/{id}/receipts       → receipts/list.html
GET  /floor21/bookings/{id}/receipts/new   → receipts/form.html
POST /floor21/bookings/{id}/receipts/save  → save and redirect
```

### Cancellations
```
GET  /floor21/bookings/{id}/cancel         → cancellations/form.html
POST /floor21/bookings/{id}/cancel/confirm → process and redirect
GET  /floor21/cancellations                → cancellations/list.html
```

### Brokers
```
GET  /floor21/brokers                 → brokers/list.html
GET  /floor21/brokers/new             → brokers/form.html
POST /floor21/brokers/save            → save and redirect
GET  /floor21/brokers/{id}/bookings   → brokers/bookings.html
```

### Slabs
```
GET  /floor21/slabs                   → slabs/list.html
GET  /floor21/slabs/new               → slabs/form.html
POST /floor21/slabs/save              → save and redirect
```

### Extra Expenses
```
GET  /floor21/bookings/{id}/expenses       → expenses/list.html
GET  /floor21/bookings/{id}/expenses/new   → expenses/form.html
POST /floor21/bookings/{id}/expenses/save  → save and redirect
```

### Super Admin
```
GET  /floor21/admin/builders          → admin/builders/list.html
GET  /floor21/admin/builders/new      → admin/builders/form.html
POST /floor21/admin/builders/save     → save and redirect
```

---

## 7. Spring Boot Project Structure

```
floor21/
├── src/
│   ├── main/
│   │   ├── java/com/floor21/
│   │   │   ├── Floor21Application.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java          ← form login, session, role-based access
│   │   │   │   ├── WebMvcConfig.java             ← interceptor registration
│   │   │   │   └── ThymeleafConfig.java          ← optional custom dialect
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── Floor21UserDetailsService.java  ← loads Builder or User by email
│   │   │   │   ├── Floor21UserPrincipal.java        ← wraps Builder/User + builderId
│   │   │   │   └── TenantContext.java               ← ThreadLocal<UUID> builderId
│   │   │   │
│   │   │   ├── interceptor/
│   │   │   │   └── TenantInterceptor.java        ← sets TenantContext from session
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── Builder.java
│   │   │   │   ├── User.java
│   │   │   │   ├── Building.java
│   │   │   │   ├── Flat.java
│   │   │   │   ├── Client.java
│   │   │   │   ├── Broker.java
│   │   │   │   ├── Booking.java
│   │   │   │   ├── Receipt.java
│   │   │   │   ├── Cancellation.java
│   │   │   │   ├── ExtraExpense.java
│   │   │   │   └── Slab.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── BuilderRepository.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── BuildingRepository.java       ← findAllByBuilderId(UUID)
│   │   │   │   ├── FlatRepository.java           ← findAllByBuildingIdAndBuilderId(...)
│   │   │   │   ├── ClientRepository.java
│   │   │   │   ├── BookingRepository.java
│   │   │   │   ├── ReceiptRepository.java
│   │   │   │   ├── BrokerRepository.java
│   │   │   │   ├── CancellationRepository.java
│   │   │   │   ├── ExtraExpenseRepository.java
│   │   │   │   └── SlabRepository.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── BuildingService.java
│   │   │   │   ├── FlatService.java             ← generateFlats(), getGridData()
│   │   │   │   ├── ClientService.java
│   │   │   │   ├── BookingService.java          ← generateBookingCode()
│   │   │   │   ├── ReceiptService.java
│   │   │   │   ├── BrokerService.java
│   │   │   │   ├── CancellationService.java
│   │   │   │   ├── SlabService.java
│   │   │   │   └── DashboardService.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java          ← GET/POST /login
│   │   │   │   ├── DashboardController.java
│   │   │   │   ├── BuildingController.java
│   │   │   │   ├── FlatController.java          ← includes /flats/data JSON endpoint
│   │   │   │   ├── ClientController.java
│   │   │   │   ├── BookingController.java
│   │   │   │   ├── ReceiptController.java
│   │   │   │   ├── BrokerController.java
│   │   │   │   ├── CancellationController.java
│   │   │   │   ├── ExtraExpenseController.java
│   │   │   │   ├── SlabController.java
│   │   │   │   └── AdminController.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── FlatGridDto.java             ← for flat grid JSON response
│   │   │   │   ├── DashboardDto.java
│   │   │   │   ├── BookingFormDto.java
│   │   │   │   ├── ClientFormDto.java
│   │   │   │   └── BuildingConfigDto.java
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java  ← @ControllerAdvice
│   │   │       ├── ResourceNotFoundException.java
│   │   │       └── UnauthorizedTenantException.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       │
│   │       ├── templates/                       ← ALL Thymeleaf templates
│   │       │   ├── layout/
│   │       │   │   ├── base.html                ← main layout with navbar + sidebar
│   │       │   │   └── fragments/
│   │       │   │       ├── navbar.html
│   │       │   │       ├── sidebar.html
│   │       │   │       └── flash-messages.html
│   │       │   ├── auth/
│   │       │   │   └── login.html
│   │       │   ├── dashboard/
│   │       │   │   └── index.html
│   │       │   ├── buildings/
│   │       │   │   ├── list.html
│   │       │   │   ├── form.html
│   │       │   │   └── flat-grid.html           ← THE INTERACTIVE FLAT BOOKING GRID
│   │       │   ├── clients/
│   │       │   │   ├── list.html
│   │       │   │   ├── form.html
│   │       │   │   └── detail.html
│   │       │   ├── bookings/
│   │       │   │   ├── list.html
│   │       │   │   ├── form.html
│   │       │   │   └── detail.html
│   │       │   ├── receipts/
│   │       │   │   ├── list.html
│   │       │   │   └── form.html
│   │       │   ├── brokers/
│   │       │   │   ├── list.html
│   │       │   │   ├── form.html
│   │       │   │   └── bookings.html
│   │       │   ├── cancellations/
│   │       │   │   ├── list.html
│   │       │   │   └── form.html
│   │       │   ├── slabs/
│   │       │   │   ├── list.html
│   │       │   │   └── form.html
│   │       │   ├── expenses/
│   │       │   │   ├── list.html
│   │       │   │   └── form.html
│   │       │   └── admin/
│   │       │       └── builders/
│   │       │           ├── list.html
│   │       │           └── form.html
│   │       │
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   └── floor21.css              ← custom styles for flat grid
│   │       │   └── js/
│   │       │       └── flat-grid.js             ← AJAX flat selection + booking logic
│   │       │
│   │       └── db/migration/
│   │           ├── V1__create_builders.sql
│   │           ├── V2__create_users.sql
│   │           ├── V3__create_buildings.sql
│   │           ├── V4__create_flats.sql
│   │           ├── V5__create_clients.sql
│   │           ├── V6__create_brokers.sql
│   │           ├── V7__create_bookings.sql
│   │           ├── V8__create_receipts.sql
│   │           ├── V9__create_cancellations.sql
│   │           ├── V10__create_extra_expenses.sql
│   │           └── V11__create_slabs.sql
│   │
│   └── test/
│       └── java/com/floor21/
│           ├── service/
│           │   ├── FlatServiceTest.java
│           │   ├── BookingServiceTest.java
│           │   └── ClientServiceTest.java
│           └── controller/
│               ├── BuildingControllerTest.java
│               └── BookingControllerTest.java
│
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

---

## 8. Thymeleaf Layout — base.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
  <meta charset="UTF-8"/>
  <title th:text="${pageTitle} + ' | Floor21'">Floor21</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css"/>
  <link rel="stylesheet" th:href="@{/floor21/css/floor21.css}"/>
</head>
<body>

<!-- Navbar -->
<nav class="navbar navbar-dark bg-primary" th:replace="layout/fragments/navbar :: navbar"></nav>

<!-- Sidebar + Content -->
<div class="container-fluid">
  <div class="row">
    <nav class="col-md-2 sidebar" th:replace="layout/fragments/sidebar :: sidebar"></nav>
    <main class="col-md-10 ms-sm-auto px-4 py-3">
      <!-- Flash messages -->
      <div th:replace="layout/fragments/flash-messages :: flash"></div>
      <!-- Page content injected here -->
      <div th:replace="${content}">Page content</div>
    </main>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
```

---

## 9. flat-grid.html (Key Thymeleaf Template)

```html
<!-- Thymeleaf renders the initial grid server-side.
     JS refreshes flat status via AJAX to /buildings/{id}/flats/data -->

<div class="row mb-3">
  <!-- Config dropdowns (stories, flats/floor, BHK mix) rendered by Thymeleaf -->
  <form th:action="@{/floor21/buildings/{id}/flats/generate(id=${building.id})}"
        method="post" class="row g-2">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <div class="col-auto">
      <label>Total Stories</label>
      <select name="totalFloors" class="form-select">
        <option th:each="n : ${#numbers.sequence(5,20)}"
                th:value="${n}" th:text="${n} + ' Floors'"></option>
      </select>
    </div>
    <div class="col-auto">
      <label>1BHK per Floor</label>
      <select name="bhk1PerFloor" class="form-select">
        <option th:each="n : ${#numbers.sequence(0,12)}"
                th:value="${n}" th:text="${n}"></option>
      </select>
    </div>
    <!-- 2BHK, 3BHK, Parking dropdowns similarly -->
    <div class="col-auto align-self-end">
      <button type="submit" class="btn btn-primary">Generate Building</button>
    </div>
  </form>
</div>

<!-- Flat Grid - rendered server-side, refreshed via JS -->
<div id="flat-grid">
  <div th:each="floor : ${floors}" class="d-flex justify-content-center align-items-center gap-2 mb-2">
    <span class="floor-label text-muted small" th:text="${floor.label}">Floor 1</span>
    <div class="d-flex gap-2 flex-wrap justify-content-center">
      <div th:each="flat : ${floor.flats}"
           th:id="'flat-' + ${flat.id}"
           th:classappend="${flat.status == 'AVAILABLE'} ? 'flat-available'
                         : (${flat.status == 'BOOKED'} ? 'flat-booked'
                         : (${flat.isParking} ? 'flat-parking' : 'flat-hold'))"
           class="flat-card"
           th:attr="data-flat-id=${flat.id},
                    data-type=${flat.bhkType},
                    data-floor=${flat.floorNumber},
                    data-price=${flat.basePrice},
                    data-status=${flat.status}"
           onclick="selectFlat(this)">
        <span class="flat-number" th:text="${flat.flatNumber}">0101</span>
        <span class="flat-type" th:text="${flat.bhkType}">2BHK</span>
      </div>
    </div>
  </div>
</div>

<!-- Booking Detail Panel (shown when flat is clicked) -->
<div id="booking-panel" class="card mt-3 d-none">
  <div class="card-body">
    <h5 id="panel-title">Flat Details</h5>
    <div class="row mb-3">
      <div class="col-md-6">
        <label>Type</label><p id="panel-type"></p>
        <label>Floor</label><p id="panel-floor"></p>
      </div>
      <div class="col-md-6">
        <label>Area</label><p id="panel-area"></p>
        <label>Price</label><p id="panel-price"></p>
      </div>
    </div>
    <div class="text-center">
      <a id="book-btn" href="#" class="btn btn-success px-5">Book this Flat</a>
    </div>
  </div>
</div>
```

---

## 10. application.yml

```yaml
spring:
  application:
    name: floor21

  datasource:
    url: jdbc:postgresql://localhost:5432/floor21_db
    username: floor21_user
    password: floor21_pass
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration

  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false        # set true in prod
    encoding: UTF-8

  security:
    filter:
      order: 10

server:
  port: 8080
  servlet:
    context-path: /floor21

floor21:
  session:
    timeout: 3600       # seconds
  app:
    name: Floor21
    version: 1.0.0
```

---

## 11. SecurityConfig.java (outline)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/floor21/login", "/floor21/css/**", "/floor21/js/**").permitAll()
                .requestMatchers("/floor21/admin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/floor21/login")
                .loginProcessingUrl("/floor21/login")
                .defaultSuccessUrl("/floor21/dashboard", true)
                .failureUrl("/floor21/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/floor21/logout")
                .logoutSuccessUrl("/floor21/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .csrf(Customizer.withDefaults());
        return http.build();
    }
}
```

---

## 12. pom.xml Key Dependencies

```xml
<dependencies>
  <!-- Spring Boot -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
  </dependency>
  <dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- PostgreSQL -->
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
  </dependency>

  <!-- Flyway -->
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>

  <!-- Lombok -->
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>

  <!-- Testing -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## 13. Docker Compose

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16
    container_name: floor21_postgres
    environment:
      POSTGRES_DB: floor21_db
      POSTGRES_USER: floor21_user
      POSTGRES_PASSWORD: floor21_pass
    ports:
      - "5432:5432"
    volumes:
      - floor21_pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U floor21_user -d floor21_db"]
      interval: 10s
      retries: 5

  floor21-app:
    build: .
    container_name: floor21_app
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/floor21_db
      SPRING_DATASOURCE_USERNAME: floor21_user
      SPRING_DATASOURCE_PASSWORD: floor21_pass
      SPRING_THYMELEAF_CACHE: "true"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  floor21_pgdata:
```

---

## 14. Key Business Rules

1. **Flat Status Flow**: `AVAILABLE` → `HOLD` → `BOOKED` → `CANCELLED` → `AVAILABLE`
2. **Booking Code**: Auto-generated on save — format `F21-{YYYY}-{SEQ:4}` e.g. `F21-2026-0042`
3. **Flat Grid**: Generated server-side by Thymeleaf from DB. JS handles selection UI and posts booking to Spring MVC controller.
4. **BHK Mix**: Building stores `bhk1_per_floor`, `bhk2_per_floor`, `bhk3_per_floor`. `FlatService.generateFlats()` creates flat records in DB based on this config.
5. **Parking Floors**: Bottom N floors auto-created with `is_parking=true`, not bookable.
6. **Multi-booking per client**: One client can book multiple flats in the same or different buildings.
7. **Receipts**: Multiple payments per booking. Total shown on booking detail page.
8. **Cancellation**: Sets flat back to `AVAILABLE`, booking status to `CANCELLED`.
9. **Tenant Isolation**: `TenantContext.getBuilderId()` used in every service method. `TenantInterceptor` sets it from Spring Security principal on every request.
10. **CSRF**: Enabled via Spring Security. Every Thymeleaf POST form must include `th:action` (auto-adds CSRF token) or manual hidden input.

---

## 15. Naming Conventions

| Item | Convention |
|---|---|
| Java packages | `com.floor21.*` |
| Table names | `snake_case` plural |
| URL base path | `/floor21/` |
| Template folder | `src/main/resources/templates/` |
| Static assets | `src/main/resources/static/css/`, `/js/` |
| App title | `Floor21` |
| Docker image | `floor21-app:latest` |
| Session attribute | `FLOOR21_BUILDER_ID` |

---

## 16. Sample Seed Data

```sql
-- Builders (tenants)
INSERT INTO builders (company_name, email, password_hash, city) VALUES
  ('Skyline Homes Pvt Ltd',   'admin@skylinehomes.com',  '{bcrypt}$2a$...', 'Mumbai'),
  ('Green Valley Developers', 'admin@greenvalley.com',   '{bcrypt}$2a$...', 'Pune'),
  ('Sunrise Realty',          'admin@sunriserealty.com', '{bcrypt}$2a$...', 'Bangalore');

-- Building for Skyline Homes
INSERT INTO buildings (builder_id, building_name, total_floors, parking_floors,
                       flats_per_floor, bhk1_per_floor, bhk2_per_floor, bhk3_per_floor)
VALUES (
  (SELECT id FROM builders WHERE email = 'admin@skylinehomes.com'),
  'Tower A - Skyline Heights', 20, 2, 6, 0, 3, 3
);
```

---

*Floor21 — Multi-tenant flat booking platform. Spring Boot 4 + Thymeleaf + PostgreSQL.*
