# Floor21 — Application Test Cases

**Purpose:** Master checklist of manual and automated test scenarios for the whole application, from **platform admin (SUPER_ADMIN)** and **partner / tenant staff (BUILDER_ADMIN, EXECUTIVE)** login through every major module.

**Scope:** UI and end-to-end behaviour. Unit/service tests are listed separately in §28.

**Environment:** Local or staging at `http://localhost/floor21` (context path `/floor21`). Fresh DB with Flyway baseline applied. Hard-refresh after deploy.

**Related docs:**
- Vault & partner-grid deep-dive: [`FLOOR21_UI_TEST_SCENARIOS.txt`](FLOOR21_UI_TEST_SCENARIOS.txt)
- Playwright setup: [`../e2e/README.md`](../e2e/README.md)
- DB reset: [`DB_MIGRATIONS.md`](DB_MIGRATIONS.md)

**Legend**

| Column | Meaning |
|--------|---------|
| **Priority** | S = Smoke, R = Regression, F = Full / edge-case |
| **Auto** | E2E = covered by Playwright; Manual = manual only; Partial = partly covered |

---

## 1. Roles & test accounts

There is **one login page** (`/login`) for all roles. Resolution order: active `users` row → else `builders` row.

| Role | UI label | How to obtain | Building scope |
|------|----------|---------------|----------------|
| **SUPER_ADMIN** | Platform admin | `builders.platform_admin = true` (e.g. `super@floor21.com`) | All tenants |
| **BUILDER_ADMIN** | Builder admin | Staff user with project role `BUILDER_ADMIN`, or legacy builder email login | All buildings in assigned project |
| **EXECUTIVE** | Partner | Staff user with role `EXECUTIVE` + building assignments | Only assigned buildings; only assigned flats bookable |

**Broker** is a CRM entity, not a login role.

### Setup checklist (before any scenario block)

- [ ] **ACC-01** Platform admin account exists and can sign in (S, Manual)
- [ ] **ACC-02** At least one tenant project (builder company) exists (S, E2E Admin — 1)
- [ ] **ACC-03** Two partner users + one builder admin user exist for that project (S, E2E Admin — 2, 4)
- [ ] **ACC-04** E2E building: **9 floors** (3 parking + 6 residential), **4 units/floor** mixed types (STUDIO, 1BHK, 2BHK, 3BHK) (S, E2E Admin — 3)
- [ ] **ACC-05** Partners assigned to buildings and ~90% flats allocated between them (R, E2E Admin — 5)
- [ ] **ACC-06** Optional: builder company login (legacy `builders` email) for BUILDER_ADMIN path (F, Manual)

---

## 2. Authentication & login page

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| AUTH-01 | Valid platform admin login | SUPER_ADMIN | Open `/login` → enter valid credentials → Sign in | Redirect to `/dashboard`; Platform sidebar visible | S | Manual |
| AUTH-02 | Valid partner login | EXECUTIVE | Same with partner credentials | Tenant dashboard; Property/Sales nav; no Platform section | S | E2E |
| AUTH-03 | Valid builder admin login | BUILDER_ADMIN | Same with builder admin credentials | Tenant dashboard; Expenses nav visible | S | Manual |
| AUTH-04 | Invalid credentials | Any | Wrong password → Sign in | Error: "Invalid email or password"; stay on login | S | Manual |
| AUTH-05 | Empty fields | Any | Submit with blank email/password | HTML5 validation / cannot submit | R | Manual |
| AUTH-06 | Logout | Any signed-in | Profile menu → Log out | Redirect to `/login?logout=true`; success message | S | Manual |
| AUTH-07 | Logo → home | Anonymous | On login page click logo | Navigates to `/` (redirects to dashboard if signed in, else login) | R | Manual |
| AUTH-08 | Theme toggle on login | Anonymous | Toggle light/dark on login page | Theme persists; login form readable in both modes | R | Manual |
| AUTH-09 | Session expired (idle) | Any | Wait past session timeout or use `?expired=true` | Warning banner: session expired due to inactivity | R | Manual |
| AUTH-10 | Session invalid (relogin) | Any | Restart server / invalidate session → navigate | Warning banner: session no longer valid | R | Manual |
| AUTH-11 | Inactive staff user | — | Login with deactivated user | Login rejected | R | Manual |
| AUTH-12 | Staff user without project assignment | — | User exists but no project assignment | Login rejected ("not assigned to a project") | F | Manual |
| AUTH-13 | Inactive builder company login | — | Legacy builder login when project inactive | Login rejected | F | Manual |
| AUTH-14 | Direct URL when unauthenticated | Anonymous | Open `/bookings` without login | Redirect to `/login` | S | Manual |
| AUTH-15 | CSRF on login form | Any | Inspect form | CSRF token present on POST | F | Manual |

---

## 3. Session, navigation & layout

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| NAV-01 | Sidebar highlights active section | Any | Navigate Buildings, Bookings, Receipts | Correct nav link has `is-active` | R | Manual |
| NAV-02 | Turbo frame navigation | Tenant | Click sidebar links | Main panel updates without full page reload | R | Manual |
| NAV-03 | Logo → dashboard | Signed-in | Click topbar logo | Goes to `/dashboard` | S | Manual |
| NAV-04 | Profile menu | Signed-in | Open profile dropdown | Shows email, My profile, Change password, Log out | S | Manual |
| NAV-05 | Vault menu conditional | EXECUTIVE / BUILDER_ADMIN | With/without vault grant | Vault link only when grant exists | S | Manual |
| NAV-06 | Expenses menu | BUILDER_ADMIN vs EXECUTIVE | Compare sidebars | Expenses only for BUILDER_ADMIN | S | Manual |
| NAV-07 | Platform menu | SUPER_ADMIN | Open dashboard | Platform section only; no tenant Property/Sales unless via impersonation | S | Manual |
| NAV-08 | Theme persistence | Any | Switch theme → reload | Theme retained (localStorage) | R | Manual |
| NAV-09 | Dark mode pagination | SUPER_ADMIN | Platform dashboard bookings pagination | No white footer strip; text readable | R | Manual |
| NAV-10 | Mobile sidebar | Any | Narrow viewport | Sidebar behaviour acceptable (hidden / accessible) | F | Manual |

---

## 4. Profile

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| PROF-01 | View profile | Any | Profile → My profile | Name, email, role info displayed | S | Manual |
| PROF-02 | Change password (valid) | Any | Change password → valid old + new | Success; can login with new password | R | Manual |
| PROF-03 | Change password (wrong old) | Any | Wrong current password | Error message; password unchanged | R | Manual |
| PROF-04 | Vault PIN link | User with vault grant | Profile / vault change PIN | PIN change flow reachable | R | Manual |

---

## 5. Dashboard

### 5.1 Platform dashboard (SUPER_ADMIN)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| DASH-P01 | Stats cards load | Open `/dashboard` | Builders, buildings, flats, bookings-this-month counts shown | S | Manual |
| DASH-P02 | Recent bookings table | — | Bookings listed with code, client, flat, building, status | S | Manual |
| DASH-P03 | Recent bookings pagination | Change page / per-page size | Showing X–Y of Z; Previous/Next work; default size 10 | R | Manual |
| DASH-P04 | Newest builders sidebar | — | Up to 5 builders with company + city | R | Manual |
| DASH-P05 | Quick links | Click Manage users / View all activity | Correct admin pages open | R | Manual |

### 5.2 Tenant dashboard (BUILDER_ADMIN / EXECUTIVE)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| DASH-T01 | Tenant stats | BUILDER_ADMIN | Open dashboard | Project-scoped metrics (not platform-wide) | S | Manual |
| DASH-T02 | Partner scoped data | EXECUTIVE | Open dashboard | Data limited to assigned buildings/bookings | R | Manual |

---

## 6. Platform admin — Projects (`/admin/projects`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| PROJ-01 | List projects | Open Projects | Paginated list; default page size 10 | S | Manual |
| PROJ-02 | Search projects | Enter name/city/email in search | Matching projects only | R | Manual |
| PROJ-03 | Filter active/inactive | Status filter All/Active/Inactive | Correct subset | R | Manual |
| PROJ-04 | Sort columns | Click sortable headers | Sort asc/desc; page resets to 0 | R | Manual |
| PROJ-05 | Create project | New project → fill required fields → Save | Project appears in list | S | E2E Admin — 1 |
| PROJ-06 | Edit project | Edit existing → change company name/city → Save | Changes persisted | R | Manual |
| PROJ-07 | Deactivate project | Deactivate active project | Status inactive; staff may be affected | R | Manual |
| PROJ-08 | Delete empty project | Delete project with no buildings/bookings | Project removed | F | Manual |
| PROJ-09 | Delete blocked | Delete project with buildings/bookings | Error / blocked with message | F | Manual |
| PROJ-10 | Per-page size | Change 10/25/50 | List size updates | R | Manual |
| PROJ-11 | Legacy URL redirect | Open `/admin/builders` | Redirects to `/admin/projects` | F | Manual |

---

## 7. Platform admin — User management (`/admin/users`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| USER-01 | List users | Open User Management | Paginated list; search + project + status filters | S | Manual |
| USER-02 | Search users | Search by name, email, company, project, role | Correct matches | R | Manual |
| USER-03 | Create user | New user → name, email, password, role, active | User saved; appears in list | S | E2E Admin — 2 |
| USER-04 | Edit user | Edit → change fields → Save | Updates persisted | R | Manual |
| USER-05 | Inactive user hidden from vault dropdown | Deactivate user | Not in Vault config user list | R | Manual |
| USER-06 | Duplicate email | Create user with existing email | Validation error | F | Manual |
| USER-07 | Vault config hint on form | Open user edit | References Platform → Vault config (no per-user vault checkbox) | R | Manual |

---

## 8. Platform admin — Partners / staff (`/admin/projects/{id}/staff`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| STAFF-01 | List partners for project | Project → Partners | Staff list with roles | S | Manual |
| STAFF-02 | Assign existing user as partner | Add partner → pick user, role EXECUTIVE, buildings | Partner added; can login | S | E2E Admin — 4 |
| STAFF-03 | Assign builder admin role | Role BUILDER_ADMIN | User gets full project building access | R | Manual |
| STAFF-04 | Building-scoped partner | Assign EXECUTIVE with 1 building only | Partner sees only that building | S | Manual |
| STAFF-05 | Remove partner from project | Remove staff | User loses project access | R | Manual |
| STAFF-06 | Create new staff from project | New partner form under project | User created + assigned | R | Manual |
| STAFF-07 | Edit partner building access | Edit staff → change buildings | Access updated | R | Manual |
| STAFF-08 | Partners list per building | `/admin/buildings/{id}/staff` | Building-filtered staff view | F | Manual |
| STAFF-09 | Impersonate partner | Impersonate button on staff row | Session switches to partner view; banner shown | R | Manual |
| STAFF-10 | End impersonation | End impersonation | Returns to platform admin; `/admin/projects` | R | Manual |

---

## 9. Platform admin — All buildings (`/admin/buildings`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| BLD-ADM-01 | List all buildings | Open All buildings | Cross-tenant list; pagination default 10 | S | Manual |
| BLD-ADM-02 | Filter by project | Project dropdown | Only that project's buildings | S | Manual |
| BLD-ADM-03 | Search buildings | Search project/building/city/layout prefix | Matching rows | R | Manual |
| BLD-ADM-04 | Clear filters | Clear filters link | Resets project + search | R | Manual |
| BLD-ADM-05 | Create building | Add building → project, name, floors, parking → Save | Building in list; flats generatable | S | E2E Admin — 3 |
| BLD-ADM-06 | Edit building | Edit building metadata | Changes saved | R | Manual |
| BLD-ADM-07 | Delete building (no bookings) | Delete | Building removed | F | Manual |
| BLD-ADM-08 | Delete blocked (has bookings) | Delete building with bookings | Blocked; "Has bookings" indicator | F | Manual |
| BLD-ADM-09 | Sort columns | Sort by project, name, city, floors, dates | Works with filters preserved | R | Manual |
| BLD-ADM-10 | Open flat grid | Flats link from row | Navigates to building flat grid | S | E2E |
| BLD-ADM-11 | Default sort newest first | New building after create | Appears near top (created desc default) | R | Manual |

---

## 10. Buildings & flat grid — tenant view (`/buildings`)

### 10.1 Building list (BUILDER_ADMIN / EXECUTIVE)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| BLD-T01 | List buildings | BUILDER_ADMIN | Open Buildings | All project buildings | S | Manual |
| BLD-T02 | Partner building scope | EXECUTIVE | Open Buildings | Only assigned buildings | S | Manual |
| BLD-T03 | Edit building layout | BUILDER_ADMIN | Edit building (no bookings constraint) | Layout form opens/saves | R | Manual |

### 10.2 Flat grid — viewing & exports

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| GRID-01 | Grid loads | Any tenant | Open `/buildings/{id}/flats` | Residential + parking sections render | S | E2E |
| GRID-02 | Parking fixtures | SUPER_ADMIN | After create with defaults | Car lift / passenger lift counts match config | R | E2E Admin — 3 |
| GRID-03 | Export Excel | BUILDER_ADMIN | Export excel | File downloads | F | Manual |
| GRID-04 | Export PDF | BUILDER_ADMIN | Export PDF / PDF grid | Files download | F | Manual |
| GRID-05 | Auto-refresh (20s) | EXECUTIVE | Wait on grid | Grid refreshes; partner restrictions preserved | R | Manual |
| GRID-06 | Floor plan image | Any tenant | View floor plan slot | Image loads | F | Manual |

### 10.3 Flat grid — partner allocation & bookability

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| GRID-P01 | Partner sees assigned flats bookable | EXECUTIVE | Login as partner 1 | Bookable count = assigned flats | S | E2E Partner — 1, 2 |
| GRID-P02 | Other partner's flat greyed | EXECUTIVE | View flat assigned to partner 2 | Not clickable; no buyer details on hover | S | Manual |
| GRID-P03 | Unassigned flat greyed | EXECUTIVE | View unassigned flat | Not bookable | S | Manual |
| GRID-P04 | Partner name on assigned card | EXECUTIVE | View assigned flat | Partner name shown | R | Manual |
| GRID-P05 | Builder admin books any flat | BUILDER_ADMIN | Click any available flat | Can hold/book regardless of partner assignment | R | Manual |
| GRID-P06 | Flat details modal | EXECUTIVE | Open bookable flat details | Modal opens; Hold/Release/Book work; close works | R | Manual |

### 10.4 Flat grid — platform admin operations (SUPER_ADMIN)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| GRID-A01 | Generate flats | Generate flats for building | Residential + parking flats created | S | E2E (implicit in building create) |
| GRID-A02 | Add / remove floors | Add floors / remove top floors | Grid updates | F | Manual |
| GRID-A03 | Floor details (BHK, etc.) | Edit floor details | Saved per floor | F | Manual |
| GRID-A04 | Parking layout config | Configure parking grid | Parking plan renders | F | Manual |
| GRID-A05 | Upload flat layout image | POST layout image | Image on flat card | F | Manual |
| GRID-A06 | Assign flat to partner | Partner flats / assign partner | Partner name on card | S | E2E Admin — 5 |
| GRID-A07 | Merge flats | Merge adjacent flats | Combined unit | F | Manual |
| GRID-A08 | Split duplex / merge split | Split operations | Units split/merged correctly | F | Manual |
| GRID-A09 | Link parking to residential | Link parking slot | Slot shows linked flat | S | E2E Admin — 6 |
| GRID-A10 | Delete flat (no booking) | Delete flat | Removed | F | Manual |
| GRID-A11 | Activate/deactivate flat | Toggle activation | Status updated | F | Manual |
| GRID-A12 | Update flat status (HOLD) | HOLD from grid/modal | Status changes; partner rules apply | R | Manual |

---

## 11. Clients (`/clients`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| CLI-01 | List clients | BUILDER_ADMIN | Open Clients | Paginated tenant clients | S | Manual |
| CLI-02 | Platform admin read-only list | SUPER_ADMIN | Open Clients from Platform | Cross-tenant list; no create | R | Manual |
| CLI-03 | Search clients | Any with access | Turbo search fragment | Live search results | R | Manual |
| CLI-04 | Create client | BUILDER_ADMIN / EXECUTIVE | New client → required fields → Save | Client in list | S | E2E Partner — 3 |
| CLI-05 | View client detail | Any | Open client | Details + linked bookings if any | R | Manual |
| CLI-06 | Edit client | BUILDER_ADMIN / EXECUTIVE | Edit → Save | Updated | R | Manual |
| CLI-07 | Platform admin cannot create | SUPER_ADMIN | Try `/clients/new` or POST save | Blocked (403 / redirect) | R | Manual |
| CLI-08 | Partner creates client for booking | EXECUTIVE | Create during booking flow | Client selectable on booking form | S | E2E |

---

## 12. Bookings (`/bookings`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| BOOK-01 | List bookings | BUILDER_ADMIN | Open Bookings | All project bookings | S | Manual |
| BOOK-02 | Partner sees own bookings only | EXECUTIVE | Open Bookings | Only bookings where partner is executive | S | E2E Partner — 4, 5 |
| BOOK-03 | Create booking (assigned flat) | EXECUTIVE | New booking → client + assigned flat + amounts | Booking ACTIVE; flat BOOKED | S | E2E Partner — 3 |
| BOOK-04 | Block booking unassigned flat | EXECUTIVE | Try to book flat assigned to other partner | Error / blocked | S | Manual |
| BOOK-05 | Builder admin books any flat | BUILDER_ADMIN | Book unassigned or any flat | Success | R | Manual |
| BOOK-06 | View booking detail | Any tenant | Open booking | Code, client, flat, amounts, status | S | Manual |
| BOOK-07 | Edit booking | BUILDER_ADMIN | Edit booking fields | Saved | R | Manual |
| BOOK-08 | Booking validation | Any | Missing required fields | Validation errors | R | Manual |
| BOOK-09 | Duplicate booking same flat | Any | Book already-booked flat | Blocked | F | Manual |
| BOOK-10 | Building access gate | EXECUTIVE | Access booking on unassigned building | Blocked / not listed | R | Manual |

---

## 13. Receipts (`/receipts`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| RCPT-01 | Receipts hub load | BUILDER_ADMIN | Open Receipts entry | Booking selector / entry form | S | Manual |
| RCPT-02 | Record receipt | BUILDER_ADMIN | Select booking → amount, date, mode → Save | Receipt saved; totals update | R | Manual |
| RCPT-03 | Print receipt | Any tenant | Print receipt | Print view opens | F | Manual |
| RCPT-04 | Legacy URL redirect | Any | Old `/bookings/{id}/receipts` URL | Redirects to hub with bookingId | F | Manual |
| RCPT-05 | Invalid amount / date | Any | Save with bad data | Error message | R | Manual |

---

## 14. Payment schedule / slabs (`/bookings/payment-schedule`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| SLAB-01 | Open schedule | BUILDER_ADMIN | Select building/booking | Slab schedule displays | S | Manual |
| SLAB-02 | Materialize schedule | BUILDER_ADMIN | Materialize from milestones | Slab rows created | R | Manual |
| SLAB-03 | Record slab payment | BUILDER_ADMIN | Save payment against slab | Payment reflected | R | Manual |
| SLAB-04 | Delete slab payment | BUILDER_ADMIN | Delete payment | Removed | F | Manual |
| SLAB-05 | Export Excel / PDF | BUILDER_ADMIN | Export | Files download | F | Manual |
| SLAB-06 | Demand draft | BUILDER_ADMIN | Generate demand draft | Document generated | F | Manual |
| SLAB-07 | Partner access | EXECUTIVE | Same flows for own bookings | Works within scope | R | Manual |

---

## 15. Platform admin — Payment milestones & rate slabs

### 15.1 Payment milestones (`/admin/payment-milestones`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| MILE-01 | List milestones | Open page | Templates listed | R | Manual |
| MILE-02 | Create milestone template | New → Save | Template saved | R | Manual |
| MILE-03 | Edit milestone | Edit → Save | Updated | R | Manual |
| MILE-04 | Import template download | Download import template | CSV/template file | F | Manual |
| MILE-05 | Import milestones | Upload CSV | Rows imported | F | Manual |

### 15.2 Rate slabs (`/admin/builder-pricing-slabs`)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| RATE-01 | List rate slabs | Open page | Slabs listed | R | Manual |
| RATE-02 | Create / edit slab | CRUD operations | Persisted | R | Manual |

---

## 16. Brokers (`/brokers`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| BRK-01 | List brokers | BUILDER_ADMIN | Open Brokers | Broker list | R | Manual |
| BRK-02 | Create broker | New broker → Save | Appears in list | R | Manual |
| BRK-03 | Edit broker | Edit → Save | Updated | R | Manual |
| BRK-04 | Broker bookings | View broker bookings | Linked bookings listed | F | Manual |
| BRK-05 | Assign broker on booking | EXECUTIVE | Create booking with broker | Association saved | F | Manual |

---

## 17. Cancellations (`/cancellations`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| CAN-01 | List cancellations | BUILDER_ADMIN | Open Cancellations | Cancelled bookings listed | R | Manual |
| CAN-02 | Cancel booking | Open cancel form → date, reason, refund → Confirm | Booking cancelled; flat released | R | Manual |
| CAN-03 | Cancel validation | Missing cancel date | Validation error | F | Manual |
| CAN-04 | Partner cancel own booking | EXECUTIVE | Cancel own booking | Allowed within scope | F | Manual |

---

## 18. Per-booking extra expenses (`/bookings/{id}/expenses`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| BEXP-01 | List booking expenses | BUILDER_ADMIN | Open expenses for booking | List shown | F | Manual |
| BEXP-02 | Add expense | New expense → Save | Row added | F | Manual |
| BEXP-03 | Separate from Vault | Compare vault booking expense vs this hub | Distinct data stores / UI | F | Manual |

---

## 19. Banking (`/bank-accounts`)

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| BANK-01 | List accounts | BUILDER_ADMIN | Open Bank accounts | Accounts listed | R | Manual |
| BANK-02 | Create account | New → Save | Account created | R | Manual |
| BANK-03 | Edit account | Edit → Save | Updated | R | Manual |
| BANK-04 | Partner access | EXECUTIVE | CRUD bank accounts | Allowed per security config | F | Manual |

---

## 20. Expenses hub (`/expenses`) — BUILDER_ADMIN only

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| EXP-01 | Menu visibility | BUILDER_ADMIN vs EXECUTIVE | Check sidebar | Expenses only for builder admin | S | Manual |
| EXP-02 | List expenses | BUILDER_ADMIN | Open Expenses | Project expenses listed | R | Manual |
| EXP-03 | Add expense | Save new expense | Row added | R | Manual |
| EXP-04 | Delete expense | Delete row | Removed | R | Manual |
| EXP-05 | Not same as Vault | Compare with Vault general expenses | Data not mixed | R | Manual |
| EXP-06 | Partner blocked | EXECUTIVE | Direct URL `/expenses` | 403 / access denied | R | Manual |

---

## 21. Vault (`/vault`) & Vault config (`/admin/vault-config`)

**Detailed scenarios:** see [`FLOOR21_UI_TEST_SCENARIOS.txt`](FLOOR21_UI_TEST_SCENARIOS.txt) sections 1–4, 8.

Summary checklist:

| ID | Scenario | Role | Expected | Priority | Auto |
|----|----------|------|----------|----------|------|
| VLT-01 | Configure grant (user + building) | SUPER_ADMIN | Grant saved | S | Manual |
| VLT-02 | Same-builder validation | SUPER_ADMIN | Cross-tenant pair rejected | S | Manual |
| VLT-03 | Vault menu with grant | BUILDER_ADMIN / EXECUTIVE | Vault visible | S | Manual |
| VLT-04 | No vault without grant | Tenant staff | No menu; URL blocked | S | Manual |
| VLT-05 | Platform admin no vault | SUPER_ADMIN | No Vault menu | S | Manual |
| VLT-06 | PIN unlock / lock / change | Granted user | PIN flow works | S | Manual |
| VLT-07 | Per-booking income & expense | Granted user | Isolated from receipts/slabs | S | Manual |
| VLT-08 | General vault income/expense | Granted user | No booking link; net total | S | Manual |
| VLT-09 | Building filter on vault | Granted user | Scoped bookings | R | Manual |
| VLT-10 | Slab schedule NOT on vault page | Granted user | Confirm removed intentionally | R | Manual |

---

## 22. Platform admin — Activity, reports, audit, settings

| ID | Scenario | Path | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| PLAT-01 | Activity log | `/admin/activity` | Open page | Recent logins/activity | R | Manual |
| PLAT-02 | Reports page | `/admin/reports` | Open page | Export links visible | R | Manual |
| PLAT-03 | Export builders CSV | Download builders.csv | File valid | F | Manual |
| PLAT-04 | Export buildings CSV | Download buildings.csv | File valid | F | Manual |
| PLAT-05 | Export inventory CSV | Download inventory.csv | File valid | F | Manual |
| PLAT-06 | Audit log | `/admin/audit-log` | Open + filter/paginate if present | Audit entries shown | R | Manual |
| PLAT-07 | Settings | `/admin/settings` | View settings | Defaults displayed | R | Manual |
| PLAT-08 | Save settings | Change setting → Save | Persisted + flash success | F | Manual |

---

## 23. Impersonation

| ID | Scenario | Role | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| IMP-01 | Start impersonation | SUPER_ADMIN | Project → Partners → Impersonate | Tenant UI as partner; banner shows | R | Manual |
| IMP-02 | Actions while impersonating | SUPER_ADMIN | View buildings/bookings as partner | Partner-scoped data | R | Manual |
| IMP-03 | End impersonation | SUPER_ADMIN | End impersonation | Back to platform admin session | R | Manual |
| IMP-04 | Non-admin cannot impersonate | BUILDER_ADMIN | POST impersonate URL | 403 | F | Manual |

---

## 24. Access control & security (cross-cutting)

| ID | Scenario | Steps | Expected | Priority | Auto |
|----|----------|-------|----------|----------|------|
| SEC-01 | Partner URL to admin | EXECUTIVE → `/admin/projects` | 403 / denied | S | Manual |
| SEC-02 | Partner cross-building data | EXECUTIVE → other building flat grid URL | Blocked / empty | S | Manual |
| SEC-03 | Super admin flat admin APIs | BUILDER_ADMIN POST generate flats | 403 | R | Manual |
| SEC-04 | Turbo frame 401 | Session expired during turbo navigation | Redirect/relogin handling | R | Manual |
| SEC-05 | CSRF on POST forms | Submit without token | Rejected | F | Manual |
| SEC-06 | Tenant isolation | Two projects; user A cannot see project B data | Enforced on lists/detail | S | Manual |

---

## 25. UI regression — list pages (pagination, search, filters)

Applies to: Projects, Users, All buildings, Clients (where implemented).

| ID | Scenario | Page | Steps | Expected | Priority | Auto |
|----|----------|------|-------|----------|----------|------|
| LIST-01 | Default page size 10 | All paginated lists | Open page | 10 rows default | R | Manual |
| LIST-02 | Per-page 25/50 | Any list | Change size | Updates; page resets | R | Manual |
| LIST-03 | Pagination prev/next | Any list | Navigate pages | Correct range text | R | Manual |
| LIST-04 | Search preserves filters | Buildings/Users/Projects | Search + paginate | Params preserved in URLs | R | Manual |
| LIST-05 | Empty state | Any list | Filter to zero results | Helpful empty message + clear link | R | Manual |
| LIST-06 | Dark theme list chrome | Any list | Dark mode | No hardcoded white panels | R | Manual |

---

## 26. End-to-end automated coverage (Playwright)

**Spec:** `e2e/tests/floor21-full-flow.spec.ts`  
**Run:** `cd e2e && npm run test:ui` (workers=1, ~10 min timeout)

| E2E ID | Test name | Maps to manual IDs |
|--------|-----------|-------------------|
| E2E-01 | Admin — 1. Create project | PROJ-05, ACC-02 |
| E2E-02 | Admin — 2. Create 2 users | USER-03, ACC-03 |
| E2E-03 | Admin — 3. Create building | BLD-ADM-05, GRID-01, GRID-02, ACC-04 |
| E2E-04 | Admin — 4. Add partners | STAFF-02, ACC-03 |
| E2E-05 | Admin — 5. Assign ~90% flats between partners | GRID-A06, GRID-P01 prep, ACC-05 |
| E2E-06 | Partner — 1. Partner 1 sees assigned flats on grid | GRID-P01 |
| E2E-07 | Partner — 2. Partner 2 sees assigned flats on grid | GRID-P01 |
| E2E-08 | Partner — 3. Both partners create clients and book ≥50% of their flats | CLI-04, BOOK-03, ACC-05 |
| E2E-09 | Admin — 6. Link parking slots to booked flats | GRID-A09 |
| E2E-10 | Partner — 4. Own bookings appear in list (partner 1) | BOOK-02 |
| E2E-11 | Partner — 5. Own bookings appear in list (partner 2) | BOOK-02 |

**Not covered by E2E (manual or future automation):** Auth, profile, vault, expenses hub, receipts, payment schedule, brokers, cancellations, banking, impersonation, platform reports/audit/settings, payment milestones, rate slabs, builder admin legacy login, super-admin client read-only, theme/session edge cases, most platform list search/filter/sort.

---

## 27. Suggested test runs

### 27.1 Smoke (~30 min, post-deploy)

- AUTH-01, AUTH-04, AUTH-06
- PROJ-05 or existing project visible (PROJ-01)
- BLD-ADM-05 or existing building (BLD-ADM-01)
- GRID-P01, BOOK-03, BOOK-02
- VLT-01, VLT-03, VLT-06 (from vault doc §9)
- NAV-09 (dark pagination)

### 27.2 Full regression (platform admin)

All §6–§9, §15, §22, §23, §25 + vault doc §1.

### 27.3 Full regression (partner)

All §10–§12, §16–§17, §19, §21 (if granted), §24 + vault doc §5–6.

### 27.4 Full regression (builder admin)

Partner regression + §20 (Expenses) + builder-admin-specific grid/booking cases.

---

## 28. Backend unit / service tests (reference)

These validate logic behind list/search pages but are not UI tests:

| Test class | Area |
|------------|------|
| `PlatformAdminServiceProjectsPageTest` | Projects pagination, search, sort, active filter |
| `AdminUserServiceUsersPageTest` | Users pagination, search, filters |
| `BuildingServiceBuildingsSearchTest` | All-buildings search |
| `StaffBuildingAccessServiceTest` | Partner building access rules |
| `FlatAdminUpdateDtoTest` | Flat admin DTO validation |

Run: `./mvnw test` from repo root.

---

## 29. Bug report template

For each failure record:

1. **Date / tester**
2. **Test ID** (e.g. BOOK-03)
3. **Role** (platform admin / builder admin / partner)
4. **URL**
5. **Steps** (numbered)
6. **Expected vs actual**
7. **Screenshot or recording**
8. **Browser + OS**
9. **Data state** (project/building/flat IDs if relevant)

---

## 30. Document history

| Date | Change |
|------|--------|
| Jun 2026 | Initial master test case catalog (admin + partner login, all modules) |

---

*End of document*
