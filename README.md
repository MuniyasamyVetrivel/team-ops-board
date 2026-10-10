# Team Ops Board

Internal Work & Performance Management System. It brings together tasks, workload, help desk, projects, approvals,
collaboration, reports and a Digital Marketing performance module, so the reporting manager can see team workload
and marketing performance at a glance.

**Status:** all 24 phases of the [implementation plan](docs/implementation-plan.md) are complete.

| Document | Contents |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | System design, packages, request flow, authentication and authorization, frontend structure, testing |
| [`docs/database.md`](docs/database.md) | Schema conventions, migrations, tables by module, history rules, indexing |
| [`docs/api.md`](docs/api.md) | Every REST endpoint with its permission, filters and sort fields |
| [`docs/digital-marketing.md`](docs/digital-marketing.md) | SEO ranking logic, target calculations, recurring activities, campaign, lead, backlink and content rules |
| [`docs/PROJECT_BRIEF.md`](docs/PROJECT_BRIEF.md) | The original requirements |
| [`docs/implementation-plan.md`](docs/implementation-plan.md) | The phase plan, what each phase delivered, and the decisions taken |

## What's inside

| Area | Modules |
|---|---|
| My work | Dashboard (role-aware), My Tasks, My Tickets, My Calendar |
| Work management | Tasks (checklists, comments, attachments, watchers, dependencies, history), Workload (LOW / NORMAL / HIGH / OVERLOADED), Projects (milestones, risks, dependencies, progress) |
| Help desk | Tickets (`TKT-000001`), SLA policies, countdowns, warnings, breaches and compliance |
| Collaboration | Approvals with configurable workflows, Announcements with acknowledgement, Knowledge Base, Documents with versions, Team directory and profiles |
| Digital Marketing | Executive dashboard, SEO pages, keywords and monthly rankings, Marketing Targets, Email Campaigns, LinkedIn Paid Campaigns, Leads, Backlinks, Content & Blog, recurring Marketing Activities, monthly report |
| Analytics | Management reports (tasks, workload, tickets, projects) with CSV export |
| Administration | Users, Departments, Settings (admin settings, role permission matrix, approval workflows), Audit Logs |
| Everywhere | Global search (Ctrl/⌘ K), notifications, light / dark / system theme |

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
| `DEV_SEED_ENABLED`, `DEV_SEED_PASSWORD` | `false`, — | Development data and users (**never in production**) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_FIRST_NAME`, `BOOTSTRAP_ADMIN_DEPARTMENT_CODE` | —, —, Rakesh, IT | Creates the first Super Admin when the users table is empty. Remove them after the first start |
| `FILE_STORAGE_DIR`, `FILE_MAX_SIZE_MB` | `./uploads`, 20 | Where uploads are stored, and the server's upload limit |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Business time zone for "today", due dates, overdue and marketing months |
| `APP_SCHEDULING_ENABLED` | `true` | Background jobs on or off |
| `TASK_REMINDER_CRON` | `0 0 8 * * *` | Daily task due-soon and overdue reminders (in `APP_TIME_ZONE`) |
| `MARKETING_ACTIVITY_CRON` | `0 10 0 * * *` | Daily job that creates recurring marketing occurrences and their tasks |
| `SERVER_PORT` | 8080 | HTTP port |
| `SERVER_FORWARD_HEADERS_STRATEGY` | — | `native` behind a reverse proxy (see Deployment) |

Business settings (workload window, default task hours, default weekly capacity, SLA warning %, marketing "behind"
threshold, upload limit) are edited in the app under **Settings** and stored in the database.

### Frontend (`frontend/.env`, optional)

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | API base URL, bundled into the build (not secret). The default goes through the Vite dev proxy in development and a same-origin reverse proxy in production |

## Development credentials

With `DEV_SEED_ENABLED=true`, 22 users are created on startup: Rakesh, plus a manager and at least one employee for
each of the 10 departments. Seeding is idempotent: existing users are left alone, and a department's manager is only
set if it has none. Every manager reports to Rakesh and every employee to their department manager. **They all use
the password set in `DEV_SEED_PASSWORD`** in `backend/.env`.

| Email | Role | Department | Use it to check |
|---|---|---|---|
| `rakesh@teamops.local` | Super Admin | IT | Everything, including Digital Marketing and administration |
| `priya.menon@teamops.local` | Department Manager | Digital Marketing | A marketing manager with every marketing permission |
| `arun.kumar@teamops.local` | Employee | Digital Marketing | SEO executive with partial marketing permissions |
| `kavya.suresh@teamops.local` | Employee | Digital Marketing | Content writer with content permissions |
| `karthik.raj@teamops.local` | Employee | Web Development | No marketing or admin access: the menus and APIs are hidden |
| `sanjay.varma@teamops.local` | Department Manager | Web Development | Karthik's manager, with no marketing access |
| `suresh.babu@teamops.local` | Department Manager | IT | Runs the IT help desk queue: assigns tickets, reopens closed ones |
| `vignesh.raman@teamops.local` | Employee + `TICKET_EDIT` | IT | Help desk agent: works on IT tickets, writes internal notes |

The other department managers are `anitha.raj` (Cyber Security), `lakshmi.priya` (HR), `ramesh.kannan` (Talent
Acquisition), `harish.prabhu` (App Development), `gokul.ravi` (Pre-Sales), `ajay.dev` (Graphic & Media) and
`revathi.sundar` (Payroll). The full list is in `DevDataSeeder.java`.

The seeders fill every module with data relative to today, each part only while its table is empty:
- About 175 tasks with a deliberately uneven load: Karthik, Priya and Deepak are overloaded.
- 19 help desk tickets, some met, at risk, breached or paused.
- Projects with milestones and risks, approvals in every state, announcements, knowledge base articles, documents and
  calendar events.
- The brief's Digital Marketing examples: SEO history, the 250 / 200 lead target, 50 / 35 / 28 / 22 backlinks, 9 of
  12 blogs, the ₹42,000 / 84-lead LinkedIn campaign, the 25,000-email Zoho campaign, and recurring activities.
  [digital-marketing.md](docs/digital-marketing.md#development-data) has the details.

These are development-only accounts on a `.local` domain. Never use them, or the seed flag, in production.

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

The backend is a self-contained Spring Boot jar. The frontend is static files. Serve both from **one origin** behind
HTTPS: the refresh cookie is `SameSite=Strict` and scoped to `/api/auth`, so the browser must see the API on the same
site as the app.

1. **Database:** create the schema and user as above on MySQL 8. Flyway migrates it when the backend starts. Back up
   the database and the upload directory together.
2. **Backend:**
   ```bash
   cd backend && ./mvnw -DskipTests package
   java -jar target/team-ops-board-backend-0.0.1-SNAPSHOT.jar
   ```
   Provide the variables above as real environment variables (or a `.env` file in the working directory):
   - `REFRESH_COOKIE_SECURE=true` and a strong `JWT_SECRET`.
   - `DEV_SEED_ENABLED=false`.
   - `FILE_STORAGE_DIR` on persistent storage.
   - `BOOTSTRAP_ADMIN_*` for the first start only, then remove them.
   - Run the background jobs (`APP_SCHEDULING_ENABLED=true`) on one instance. They are idempotent, but there's no
     reason to run them twice.
   - Behind the reverse proxy, set `SERVER_FORWARD_HEADERS_STRATEGY=native` so audit logs record the client's IP
     from `X-Forwarded-For` (only internal proxy addresses are trusted) instead of the proxy's.
   - Health check: `GET /actuator/health`.
3. **Frontend:**
   ```bash
   cd frontend && npm ci && npm run build      # outputs dist/
   ```
   Serve `dist/` with a web server that falls back to `index.html` for client-side routes. Cache `assets/*` for a
   long time (the file names are content-hashed) and `index.html` not at all.
4. **Reverse proxy:** send `/api/` to the backend and everything else to `dist/`. For example, with nginx:
   ```nginx
   location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; }
   location /assets/ { root /srv/team-ops-board; expires 1y; add_header Cache-Control "public, immutable"; }
   location / { root /srv/team-ops-board; try_files $uri /index.html; add_header Cache-Control "no-cache"; }
   ```
   Set `client_max_body_size` to at least `FILE_MAX_SIZE_MB`. `CORS_ALLOWED_ORIGINS` only matters if the API is
   called from another origin.

## Authentication and authorization (summary)

- Email and password login with BCrypt. A short-lived access JWT is kept in memory, and a rotating refresh token sits
  in an httpOnly cookie, hashed in the database.
- The server reloads the user, roles, department and permissions on every request and never trusts frontend claims.
- Roles: `SUPER_ADMIN`, `DEPARTMENT_MANAGER`, `EMPLOYEE`. Each endpoint checks a permission (`@PreAuthorize`), queries
  are limited to the viewer's data scope, and administration never allows privilege escalation.
- Digital Marketing is visible only to Super Admins and people granted `MARKETING_VIEW`.

Details are in [docs/architecture.md](docs/architecture.md#authentication-and-authorization).
