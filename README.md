# Team Ops Board

Internal Work & Performance Management System. It brings together tasks, workload, help desk, projects, approvals,
collaboration, reports, a Digital Marketing performance module and department tools (Bench Management, Vendors,
Finance Tracker), so the reporting manager can see team workload and marketing performance at a glance.

**Status:** everything planned is built: all 24 phases of the [implementation plan](docs/implementation-plan.md), the
UI revamp, the calendar celebrations, the go-live data work and expansion phases X1–X8 of the
[expansion brief](docs/EXPANSION_BRIEF.md). The plan's status table lists what each one delivered.

| Document | Contents |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | System design, packages, request flow, authentication and authorization, frontend structure, testing |
| [`docs/database.md`](docs/database.md) | Schema conventions, migrations, tables by module, history rules, indexing |
| [`docs/api.md`](docs/api.md) | Every REST endpoint with its permission, filters and sort fields |
| [`docs/digital-marketing.md`](docs/digital-marketing.md) | SEO ranking logic, target calculations, recurring activities, campaign, lead, backlink and content rules |
| [`docs/go-live-data.md`](docs/go-live-data.md) | Resetting a database, the first Super Admin and loading the real team |
| [`docs/deployment.md`](docs/deployment.md) | Production on Lightsail with Docker Compose and a Cloudflare Tunnel: first deployment, checks, update, rollback, restore |
| [`docs/backup-and-restore.md`](docs/backup-and-restore.md) | Encrypted backups, restore scripts and the monthly restore test, "Export all data", break-glass recovery |
| [`docs/adding-a-module.md`](docs/adding-a-module.md) | The recipe for a new department tool (module registry, switch, permissions, pages) |
| [`docs/PROJECT_BRIEF.md`](docs/PROJECT_BRIEF.md) | The original requirements |
| [`docs/EXPANSION_BRIEF.md`](docs/EXPANSION_BRIEF.md) | Expansion phases X1–X8 and the decisions agreed during them |
| [`docs/CALENDAR_FEATURES_BRIEF.md`](docs/CALENDAR_FEATURES_BRIEF.md), [`docs/UI_REVAMP_BRIEF.md`](docs/UI_REVAMP_BRIEF.md) | The calendar celebrations and UI revamp requirements |
| [`docs/implementation-plan.md`](docs/implementation-plan.md) | The phase plan, what each phase delivered, and the decisions taken |

## What's inside

| Area | Modules |
|---|---|
| My work | Dashboard (role-aware), My Tasks, My Tickets, My Calendar (with birthdays, work anniversaries, important days and events) |
| Work management | Tasks (checklists, comments, attachments, watchers, dependencies, history), Workload (LOW / NORMAL / HIGH / OVERLOADED), Projects (milestones, risks, dependencies, progress) |
| Help desk | Tickets (`TKT-000001`), SLA policies, countdowns, warnings, breaches and compliance |
| Collaboration | Approvals with configurable workflows, Announcements with acknowledgement, Knowledge Base, Documents with versions, Team directory and profiles with employee IDs and photos |
| Celebrations | Employees, Important Days, Events and LinkedIn posts, with daily reminders |
| Digital Marketing | Executive dashboard, SEO pages, keywords and monthly rankings, Marketing Targets, Email Campaigns, LinkedIn Paid Campaigns, Leads, Backlinks, Content & Blog, recurring Marketing Activities, monthly report |
| Analytics | Management reports (tasks, workload, tickets, projects) with CSV export |
| Department tools | Bench Management (consultants, clients, engagements, allocations, bench and utilisation), Vendors (contacts, contracts, renewals), Finance Tracker (tools and licences, expenses with receipts, budgets, exchange rates, dashboard). Each is switched on per department |
| Administration | Users, Departments, Settings (admin settings, role permission matrix, approval workflows, modules), Audit Logs, Recycle Bin, Backups (status and "Export all data") |
| Everywhere | History tab on every record, recycle bin with Undo, global search (Ctrl/⌘ K), notifications, light / dark / system theme |

History, Audit Logs, Recycle Bin, Backups, Export, Modules and each department tool have their own permission, held
only by the Super Admin until it is granted to someone ([expansion brief, Access](docs/EXPANSION_BRIEF.md)). Finance
data (Finance Tracker records, vendor contract values, finance amounts in history and audit) stays hidden from
everyone without `FINANCE_VIEW`.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT, BCrypt), Spring Data JPA / Hibernate 7, Flyway, Maven |
| Database | MySQL 8 |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 8, Tailwind CSS 4, shadcn/ui-style components, TanStack Query, React Hook Form + Zod, Recharts |

## Installation

### Prerequisites

- **JDK 21**: `JAVA_HOME` must point to it (`mvn -v` should report Java 21). The build fails fast on older JDKs.
- **Maven 3.9+**, or the included wrapper (`mvnw` / `mvnw.cmd`).
- **Node.js 22.22+ or 24+** and npm.
- **MySQL 8** on `localhost:3306`.

### 1. Create the database (one time)

Run as a MySQL admin user (for example `root`):

```sql
CREATE DATABASE team_ops_board CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'teamops'@'localhost' IDENTIFIED BY '<the DB_PASSWORD from backend/.env>';
GRANT ALL PRIVILEGES ON team_ops_board.* TO 'teamops'@'localhost';
-- Integration tests (mvnw verify -Pit) use their own database, never the real one:
CREATE DATABASE team_ops_board_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON team_ops_board_it.* TO 'teamops'@'localhost';
FLUSH PRIVILEGES;
```

Flyway creates and migrates every table on the first backend start.

### 2. Configure the environment

```bash
cp backend/.env.example backend/.env    # then fill in real values
```

`backend/.env` is git-ignored and read automatically by Spring Boot. Real environment variables override it.

### 3. Run

```bash
# Backend: http://localhost:8080
cd backend
./mvnw spring-boot:run                                   # add -Dspring-boot.run.profiles=dev for SQL logging

# Frontend: http://localhost:5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

## Environment variables

### Backend (`backend/.env`)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | — | MySQL connection. Keep `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` in the URL |
| `JWT_SECRET` | — | HS256 signing key, **at least 32 bytes** (`openssl rand -base64 48`) |
| `JWT_ACCESS_TTL_MINUTES` / `JWT_REFRESH_TTL_DAYS` | 60 / 7 | Access token and session lifetimes |
| `REFRESH_COOKIE_SECURE` | `true` | `true` behind HTTPS; `false` only for local http |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call the API |
| `DEV_SEED_ENABLED` | `false` | Keep `false`. Demo data lives only in the automated tests |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_FIRST_NAME`, `BOOTSTRAP_ADMIN_LAST_NAME`, `BOOTSTRAP_ADMIN_DEPARTMENT_CODE`, `BOOTSTRAP_ADMIN_EMPLOYEE_ID` | —, —, Admin, —, IT, — | Creates the first Super Admin when the users table is empty. The password is temporary and must be changed at first sign-in. Remove the password after the first start |
| `FILE_STORAGE_DIR`, `FILE_MAX_SIZE_MB` | `./uploads`, 20 | Where uploads are stored, and the server's upload limit |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Business time zone for "today", due dates, overdue and marketing months |
| `APP_SCHEDULING_ENABLED` | `true` | Background jobs on or off |
| `TASK_REMINDER_CRON` | `0 0 8 * * *` | Daily task due-soon and overdue reminders (in `APP_TIME_ZONE`) |
| `MARKETING_ACTIVITY_CRON` | `0 10 0 * * *` | Daily job that creates recurring marketing occurrences and their tasks |
| `CELEBRATION_REMINDER_CRON` | `0 0 8 * * *` | Daily birthday, work anniversary, important day and event reminders |
| `BENCH_REMINDER_CRON`, `VENDOR_REMINDER_CRON`, `FINANCE_REMINDER_CRON` | `0 15 8 * * *`, `0 20 8 * * *`, `0 25 8 * * *` | Daily roll-off (30/14 days), contract renewal (30/7) and licence renewal (30/7) reminders, to holders of the tool's view permission |
| `RECYCLE_BIN_PURGE_CRON` | `0 30 2 * * *` | Daily permanent delete of recycle bin items older than the retention setting (default 90 days) |
| `BACKUP_ENABLED`, `BACKUP_CRON`, `BACKUP_ENCRYPTION_KEY`, `MYSQLDUMP_PATH`, `BACKUP_TARGET`, `BACKUP_LOCAL_PATH`, `BACKUP_S3_*`, `BACKUP_SECONDARY_*`, `BACKUP_KEEP_DAILY`, `BACKUP_KEEP_MONTHLY`, `BACKUP_MAX_AGE_HOURS`, `BACKUP_WORK_DIR` | see `.env.example` | Nightly encrypted backup and its copies, retention and the health alert ([docs/backup-and-restore.md](docs/backup-and-restore.md)) |
| `COMPANY_NAME` | `Tritusa` | Shown on work anniversaries ("5 years at Tritusa") |
| `SERVER_PORT` | 8080 | HTTP port |
| `SERVER_FORWARD_HEADERS_STRATEGY` | — | `native` behind a reverse proxy (see Deployment) |

Business settings (workload window, default task hours, default weekly capacity, SLA warning %, marketing "behind"
threshold, celebration reminder days, recycle bin retention, upload limit, finance default currency) are edited in the
app under **Settings** and stored in the database.

### Frontend (`frontend/.env`, optional)

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | API base URL, bundled into the build (not secret). The default goes through the Vite dev proxy in development and a same-origin reverse proxy in production |

## Users and first sign-in

The app has no demo users. On an empty database, `BOOTSTRAP_ADMIN_*` creates the first Super Admin. Everyone else
is added in **Admin → Users**, one at a time or with **Import CSV**. The import checks every row first, shows valid
and invalid rows, and returns each new person's temporary password once, in a file to download.

Anyone whose password was set by an administrator (new account, import, bootstrap or reset) must choose their own
password before using the app. The backend refuses every other request until they do. Passwords need at least 10
characters, with letters and numbers.

[docs/go-live-data.md](docs/go-live-data.md) walks through resetting a database and loading the real team.

The demo seeders (`DevDataSeeder` and its module seeders) live in the **test sources** only, so a built application
cannot create demo data. Automated tests never depend on them: each test creates its own users and records.

## Build and test

```bash
# Backend: compile, unit and MVC slice tests (no database needed)
cd backend && ./mvnw verify

# Backend: also the integration tests against MySQL (Flyway, schema validation, full flows, query budget)
cd backend && ./mvnw verify -Pit

# Frontend
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

`QueryBudgetIT`, part of `-Pit`, fails if any dashboard, report or list endpoint starts running an N+1 query,
exceeds its statement budget, or scans a growing table without an index.

## Deployment

Production runs on one Amazon Lightsail instance (Ubuntu 24.04, 4 GB, Sydney) as a Docker Compose stack, reachable
only through a Cloudflare Tunnel at **https://ops.tritusa.com.au**. **[docs/deployment.md](docs/deployment.md)** has
the first deployment, the checks, updates, rollbacks, restores and break-glass steps.

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | nginx, backend, mysql 8.4, cloudflared (and an on-demand `tools` container for restores). No host ports, named volumes for MySQL data, uploads and local backups, health checks, restart policies, memory limits for 4 GB |
| `.env.prod.example` | Every production variable as a placeholder (copy to `.env.prod` on the server) |
| `backend/Dockerfile` | JDK 21 build, JRE 21 runtime as a non-root user with the MySQL 8.4 client (`runtime`), and the restore image (`tools`) |
| `frontend/Dockerfile`, `deploy/nginx/team-ops.conf.template` | Node build, non-root Nginx: SPA fallback, `/api` proxy, gzip, 10 MB uploads, security headers and CSP, real client IP from Cloudflare |
| `backend/src/main/resources/application-prod.yml` | The `prod` Spring profile: Flyway on, demo data off, CORS for the production origin only, Secure cookie, actuator health only, logs to stdout, `Asia/Kolkata`, forwarded headers trusted from Nginx only |

Elsewhere, the same rules apply: serve the app and `/api` from **one origin** behind HTTPS (the refresh cookie is
`SameSite=Strict` and scoped to `/api/auth`), run the backend with `SPRING_PROFILES_ACTIVE=prod` (after adjusting its
origin and proxy network), give it `mysqldump` from MySQL 8.4, and have the reverse proxy **set** (not append)
`X-Forwarded-For` to the client's address.

## Authentication and authorization (summary)

- Email and password login with BCrypt. A short-lived access JWT is kept in memory, and a rotating refresh token sits
  in an httpOnly cookie, hashed in the database.
- The server reloads the user, roles, department and permissions on every request and never trusts frontend claims.
- Roles: `SUPER_ADMIN`, `DEPARTMENT_MANAGER`, `EMPLOYEE`. Each endpoint checks a permission (`@PreAuthorize`), queries
  are limited to the viewer's data scope, and administration never allows privilege escalation.
- Digital Marketing is visible only to Super Admins and people granted `MARKETING_VIEW`.
- If no Super Admin can sign in, recovery runs from the server command line only (`scripts/break-glass.ps1` / `.sh`,
  see [docs/backup-and-restore.md](docs/backup-and-restore.md)).

Details are in [docs/architecture.md](docs/architecture.md#authentication-and-authorization).
