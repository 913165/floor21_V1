# Floor21 — Playwright E2E tests

Browser UI tests for the Spring Boot app. This folder is **additive only**: it does not change `src/`, `pom.xml`, or the Maven build unless you wire that up later.

## Prerequisites

Same as [QUICKSTART.md](../QUICKSTART.md):

- JDK 25, PostgreSQL (`floor21_db`), app on **http://localhost/floor21** (port **80**, context path `/floor21`).

## One-time setup

```powershell
cd C:\work_floor21\floor21\e2e
npm install
npm run install:browsers
```

## Run tests (app already running)

Terminal 1 — start the app:

```powershell
cd C:\work_floor21\floor21
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
.\mvnw.cmd spring-boot:run
```

Terminal 2 — run Playwright:

```powershell
cd C:\work_floor21\floor21\e2e
npm test
```

Other commands:

| Command | Purpose |
|---------|---------|
| `npm run test:ui` | Interactive UI mode |
| `npm run test:headed` | See the browser (one test at a time) |
| `npm run test:headed:slow` | Headed, ~2.5s pause between each action |
| `npm run test:headed:slower` | Headed, ~5s pause between each action |
| `npm run test:headed:watch` | Headed, ~8s pause — easiest to follow step by step |
| `npm run test:debug` | Step-through debugger |
| `npm run codegen` | Record steps (`npm run codegen -- http://localhost/floor21/login`) |
| `npm run report` | Open last HTML report |

### Watch tests slowly (headed)

`test:headed` runs fast by default. Use a slower script or pick one test:

```powershell
npm run test:headed:watch
# or:
npm run test:headed:slower
npm run test:headed:slow
# one test only:
npx playwright test tests/login.spec.ts --headed --config=playwright.watch.config.ts
```

Edit `slowMo` in `playwright.watch.config.ts` (milliseconds) if you want it even slower.

For full manual control (pause, step, rerun), prefer **`npm run test:ui`** — click a test, then use the pause button on the timeline.

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
├── helpers/projects.ts  # project list/form helpers
├── helpers/users.ts     # user list/form helpers
├── helpers/buildings.ts # All buildings create helpers
├── tests/
│   ├── login.spec.ts
│   ├── admin-users.spec.ts
│   ├── admin-users-create.spec.ts
│   ├── admin-projects.spec.ts
│   ├── admin-buildings-create.spec.ts
│   └── floor21-full-flow.spec.ts
└── package.json
```

Seeded login: `super@floor21.com` / `super123` (platform super admin only).

**DB note:** `floor21-full-flow.spec.ts` inserts a full tenant + booking per run (use `--workers=1`). Other specs insert their own test data. None delete rows afterward.

### Full flow in Playwright UI (admin + partner, step by step)

```powershell
cd C:\work_floor21\floor21\e2e
npm run test:ui -- tests/floor21-full-flow.spec.ts --workers=1
```

Expand **Floor21 — full flow (admin + partner)**, then click ▶ on each step in order.

**Admin before partner:** Partner steps read credentials from `e2e/.flow-state.json`, written after each admin step. Run **Admin — 1** through **Admin — 5** before any **Partner —** step (or run the full file top to bottom). If partner login fails with “Flow state missing”, run the admin steps first.

**Credentials output:** After each admin step (and before partner login), emails/passwords are printed to the terminal and Playwright UI **Log** tab, attached under **Attachments**, and saved to `e2e/.flow-credentials.txt` (open that file anytime to copy login details).

**Note:** `baseURL` must include a trailing slash (`http://localhost/floor21/`) so paths resolve under the Spring context path. Use relative paths like `page.goto('login')`, not `page.goto('/login')`.

## Turbo frames

In-app navigation updates `#floor21-main`. Use `mainPanel(page)` from `helpers/auth.ts` when asserting content inside the main panel (it is a turbo-frame, not an iframe).
