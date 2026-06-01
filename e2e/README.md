# Floor21 — Playwright E2E tests

Browser UI tests for the Spring Boot app. This folder is **additive only**: it does not change `src/`, `pom.xml`, or the Maven build unless you wire that up later.

## Prerequisites

Same as [QUICKSTART.md](../QUICKSTART.md):

- JDK 25, PostgreSQL (`floor21_db`), app on **http://localhost/floor21** (port **80**, context path `/floor21`).

## One-time setup

```powershell
cd C:\work_floor21\floor21_V1\e2e
npm install
npm run install:browsers
```

## Run tests (app already running)

Terminal 1 — start the app:

```powershell
cd C:\work_floor21\floor21_V1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
.\mvnw.cmd spring-boot:run
```

Terminal 2 — run Playwright:

```powershell
cd C:\work_floor21\floor21_V1\e2e
npm test
```

Other commands:

| Command | Purpose |
|---------|---------|
| `npm run test:ui` | Interactive UI mode |
| `npm run test:headed` | See the browser |
| `npm run test:debug` | Step-through debugger |
| `npm run codegen` | Record steps (`npm run codegen -- http://localhost/floor21/login`) |
| `npm run report` | Open last HTML report |

## Optional: let Playwright start Spring Boot

```powershell
$env:FLOOR21_START_SERVER = "1"
npm test
```

This runs `mvnw spring-boot:run` from the repo root (slow first boot; Flyway + seed data).

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `FLOOR21_BASE_URL` | `http://localhost/floor21` | App base URL |
| `FLOOR21_START_SERVER` | unset | Set to `1` to start Spring Boot via `webServer` in config |

## Layout

```
e2e/
├── playwright.config.ts
├── helpers/auth.ts      # login helpers, turbo frame locator
├── tests/
│   ├── login.spec.ts
│   └── admin-users.spec.ts
└── package.json
```

Demo users (seeded): `super@floor21.com` / `super123`, `admin@skylinehomes.com` / `admin123`.

## Turbo frames

In-app navigation updates `#floor21-main`. Use `mainFrame(page)` from `helpers/auth.ts` when asserting content inside the main panel.
