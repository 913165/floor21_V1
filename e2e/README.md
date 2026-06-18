# Floor21 — Playwright E2E tests

Browser UI tests for the Spring Boot app. This folder is **additive only**: it does not change `src/`, `pom.xml`, or the Maven build unless you wire that up later.

## Prerequisites

Same as [QUICKSTART.md](../QUICKSTART.md):

- JDK 26, PostgreSQL (`floor21_db`), app on **http://localhost/floor21** (port **80**, context path `/floor21`).

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
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26"
.\mvnw.cmd spring-boot:run
```

Terminal 2 — run the full flow test:

```powershell
cd C:\work_floor21\floor21\e2e
npm test
```

Or open the Playwright UI (pick and run steps):

```powershell
npm run test:ui
```

Same as:

```powershell
npx playwright test --ui tests/floor21-full-flow.spec.ts --workers=1
```

Other commands:

| Command | Purpose |
|---------|---------|
| `npm run test:ui` | Interactive UI — only `floor21-full-flow.spec.ts` |
| `npm run test:headed` | Headed run, one worker |
| `npm run test:debug` | Step-through debugger |
| `npm run report` | Open last HTML report |

For full manual control (pause, step, rerun), use **`npm run test:ui`**.

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
├── helpers/             # shared by floor21-full-flow
├── tests/
│   └── floor21-full-flow.spec.ts
└── package.json
```

Seeded login: `super@floor21.com` / `super123` (platform super admin only).

**DB note:** `floor21-full-flow.spec.ts` inserts a full tenant run: ~**90%** of residential flats split between two partners; **each partner** creates clients and bookings for at least **50%** of their own assigned flats. Always use **`--workers=1`**. Test data is not deleted after the run. Allow up to **10 minutes** (large flat assignment + multiple bookings).

### Full flow in Playwright UI (admin + partner, step by step)

```powershell
cd C:\work_floor21\floor21\e2e
npm run test:ui
```

Expand **Floor21 — full flow (admin + partner)**, then click ▶ on each step in order.

**Admin before partner:** Partner steps read credentials from `e2e/.flow-state.json`, written after each admin step. Run **Admin — 1** through **Admin — 5b** before any **Partner —** step (or run the full file top to bottom). If partner login fails with “Flow state missing”, run the admin steps first.

**Admin — 3e (floor configure):** Ground floor — **Add ground floor** + configure shops; parking floor 1 — open Configure and save. Size sliders were removed; panel edge-drag handles resize instead. Tests verify modals have no car/shop size bars and saved config keeps default size % (140 shops / 180 parking). See `helpers/floor-size-config.ts`.

**Partner — 6 (receipts):** After bookings exist, records **5 waterfall receipts** for **Client1 Buyer** (`client1@example.com`) across milestone slabs 1–3 (₹50L consideration), plus one receipt for partner 2’s first client, then attaches **payment schedule** full-page screenshots to the Playwright report.

**Passwords:** E2E-created partner users all use **`user123`**. Platform admin stays `super@floor21.com` / `super123`. Clients are CRM records only (no login).

**Credentials output:** After each admin step (and before partner login), emails/passwords are printed to the terminal and Playwright UI **Log** tab, attached under **Attachments**, and saved to `e2e/.flow-credentials.txt` (open that file anytime to copy login details).

**Note:** `baseURL` must include a trailing slash (`http://localhost/floor21/`) so paths resolve under the Spring context path. Use relative paths like `page.goto('login')`, not `page.goto('/login')`.

**Navigation:** After login, helpers in `helpers/nav.ts` reach screens through the **sidebar** (same as manual testing). Direct `page.goto` is reserved for login and HTTP API calls (e.g. milestone template download), not for in-app tenant screens.

**Modals:** Helpers dismiss open modals with the inline **Close** button (flat details `#admin-close-btn`) or footer **Close** / **Cancel** where available, so Playwright is not blocked by the backdrop. Header **X** is only used when no footer dismiss control exists (e.g. receipt form).

## Turbo frames

In-app navigation updates `#floor21-main`. Use `mainPanel(page)` from `helpers/auth.ts` when asserting content inside the main panel (it is a turbo-frame, not an iframe).
