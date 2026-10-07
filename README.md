# Team Ops Board

Internal Work & Performance Management System: tasks, workload, help desk, projects, approvals, collaboration, reports and a Digital Marketing performance module, so the reporting manager can see team workload and marketing performance at a glance.

- Requirements: [`docs/PROJECT_BRIEF.md`](docs/PROJECT_BRIEF.md)
- Architecture, schema and phase plan: [`docs/implementation-plan.md`](docs/implementation-plan.md)
- API reference: [`docs/api.md`](docs/api.md)

**Status:** Phases 3–5 complete: authentication; users, departments, roles and the team directory; tasks, My Tasks and workload. Other sidebar modules show a placeholder naming the phase that builds them.

## Stack

| | |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT, BCrypt), Spring Data JPA / Hibernate 7, Flyway, Maven |
| Database | MySQL 8 |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 8, Tailwind CSS 4, shadcn/ui-style components, TanStack Query, React Hook Form + Zod |

## Prerequisites

- **JDK 21**: `JAVA_HOME` must point to it (`mvn -v` should report Java 21). The build fails fast on older JDKs.
- **Maven 3.9+**, or use the included wrapper (`mvnw` / `mvnw.cmd`).
- **Node.js 22.22+ or 24+** and npm.
- **MySQL 8** on `localhost:3306`.

## 1. Create the database (one time)

Run as a MySQL admin user (for example `root`):

```sql
CREATE DATABASE team_ops_board CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'teamops'@'localhost' IDENTIFIED BY '<the DB_PASSWORD from backend/.env>';
GRANT ALL PRIVILEGES ON team_ops_board.* TO 'teamops'@'localhost';
FLUSH PRIVILEGES;
```

Flyway creates every table on the first backend start.

## 2. Configure environment

```bash
cp backend/.env.example backend/.env    # then fill in real values
```

`backend/.env` is git-ignored and read automatically by Spring Boot. Real environment variables override it. Key settings:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | HS256 signing key, **at least 32 bytes** (`openssl rand -base64 48`) |
| `JWT_ACCESS_TTL_MINUTES` / `JWT_REFRESH_TTL_DAYS` | Session lifetimes (default 60 min / 7 days) |
| `REFRESH_COOKIE_SECURE` | `true` behind HTTPS; `false` only for local http |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API |
| `DEV_SEED_ENABLED`, `DEV_SEED_PASSWORD` | Development users (never enable in production) |
| `BOOTSTRAP_ADMIN_*` | Creates the first Super Admin on an empty production database |
| `FILE_STORAGE_DIR`, `FILE_MAX_SIZE_MB` | Where task attachments are stored (default `./uploads`) and the upload limit (default 20 MB) |
| `APP_TIME_ZONE` | Business time zone for "due today" and "overdue" (default `Asia/Kolkata`) |

The frontend needs no `.env` in development. See `frontend/.env.example` to point a build at another API host.

## 3. Run

```bash
# Backend: http://localhost:8080
cd backend
./mvnw spring-boot:run                                   # add -Dspring-boot.run.profiles=dev for SQL logging

# Frontend: http://localhost:5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

## Development credentials

With `DEV_SEED_ENABLED=true`, 22 users are created on startup: Rakesh, plus a manager and at least one employee for each of the 10 departments. Seeding is idempotent, so existing users are left alone, and a department's manager is only set if it has none. Every manager reports to Rakesh, and every employee reports to their department manager. They all use the password set in `DEV_SEED_PASSWORD` in `backend/.env`.

The most useful accounts for testing:

| Email | Role | Department | Purpose |
|---|---|---|---|
| `rakesh@teamops.local` | Super Admin | IT | Sees and manages everything |
| `priya.menon@teamops.local` | Department Manager | Digital Marketing | Manager with all marketing permissions |
| `arun.kumar@teamops.local` | Employee | Digital Marketing | SEO executive, partial marketing permissions |
| `kavya.suresh@teamops.local` | Employee | Digital Marketing | Content writer, content permissions |
| `karthik.raj@teamops.local` | Employee | Web Development | No marketing or admin access: checks that the menus and APIs are hidden |
| `sanjay.varma@teamops.local` | Department Manager | Web Development | Karthik's manager, with no marketing access |

The other departments follow the same pattern. Their managers are `suresh.babu` (IT), `anitha.raj` (Cyber Security), `lakshmi.priya` (HR), `ramesh.kannan` (Talent Acquisition), `harish.prabhu` (App Development), `gokul.ravi` (Pre-Sales), `ajay.dev` (Graphic & Media) and `revathi.sundar` (Payroll). The full list is in `DevDataSeeder.java`.

The seeder also creates 3 projects and about 175 tasks, with due dates relative to today. This happens only when the tasks table is empty. The tasks give some people a deliberately heavy load: Karthik Raj, Priya Menon and Deepak Nair are overloaded, Arun Kumar and Sanjay Varma are high, and most others are low. That means the Overdue, Due today and Workload views have realistic content straight away.

These are development-only accounts on a `.local` domain. Never use them, or the seed flag, in production.

## Build & test

```bash
# Backend: compile + unit tests (no database needed)
cd backend && ./mvnw verify

# Backend: integration tests against MySQL (Flyway migration, schema validation, full auth flow)
cd backend && ./mvnw verify -Pit

# Frontend
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
```

## Authentication & authorization (summary)

- **Login:** email and password, checked against a BCrypt hash (strength 12). The response contains a short-lived **access JWT** (60 min, HS256) and sets an **httpOnly, SameSite=Strict refresh cookie** scoped to `/api/auth`.
- **Refresh tokens rotate on every use.** Only their SHA-256 hash is stored. Re-use of a revoked token revokes all of that user's sessions.
- **The frontend keeps the access token in memory only.** On page load it restores the session through `POST /api/auth/refresh`. On a 401 it refreshes once and retries. If the refresh fails, it returns to `/login?expired=1`.
- **The backend decides who the user is and what they can do.** On every request it reloads the user, roles, department and permissions from the database; JWT claims are never trusted. Disabling a user or revoking a permission takes effect on their next request.
- **Roles:** `SUPER_ADMIN` (all permissions), `DEPARTMENT_MANAGER`, `EMPLOYEE`. Permissions such as `TASK_EDIT` or `SEO_VIEW` are Spring authorities used with `@PreAuthorize`. Digital Marketing access is a direct grant (`MARKETING_VIEW` etc.).
- **Data scope** (`AccessScopeService`):
  - A Super Admin sees everything.
  - A Department Manager sees their own department, any department where they are the manager, and any department where they hold a co-manager membership.
  - An employee sees their own records.
  - Later modules (tasks, workload, tickets) filter their queries through this scope.
- **No privilege escalation:**
  - An administrator can only grant permissions they hold themselves.
  - Only a Super Admin can grant Super Admin, or disable or demote one.
  - Nobody can disable themselves or change their own access.
  - The last active Super Admin can't be removed.
- **Audit:** these events are written to `audit_logs`:
  - logins, failed logins (with the reason) and logouts
  - refresh-token re-use
  - user create, update, disable/enable and password reset
  - role and permission changes, recorded with before and after values
  - department create and update, and member changes
