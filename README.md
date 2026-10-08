# Team Ops Board

Internal Work & Performance Management System: tasks, workload, help desk, projects, approvals, collaboration, reports and a Digital Marketing performance module, so the reporting manager can see team workload and marketing performance at a glance.

- Requirements: [`docs/PROJECT_BRIEF.md`](docs/PROJECT_BRIEF.md)
- Architecture, schema and phase plan: [`docs/implementation-plan.md`](docs/implementation-plan.md)
- API reference: [`docs/api.md`](docs/api.md)

**Status:** Phases 3–7 complete: authentication; users, departments, roles and the team directory; tasks, My Tasks and workload; the role-aware home dashboard, in-app notifications and the calendar API; the help desk (tickets, My Tickets) with SLA tracking; projects, approvals, announcements, the knowledge base, documents and the calendar. Other sidebar modules show a placeholder naming the phase that builds them.

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
| `APP_SCHEDULING_ENABLED`, `TASK_REMINDER_CRON` | Background jobs; the daily task due-soon/overdue reminders run at 08:00 in `APP_TIME_ZONE` by default |

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
| `suresh.babu@teamops.local` | Department Manager | IT | Runs the IT help desk queue: assigns tickets, reopens closed ones |
| `vignesh.raman@teamops.local` | Employee + `TICKET_EDIT` | IT | Help desk agent: works on IT tickets, writes internal notes |

The other departments follow the same pattern. Their managers are `suresh.babu` (IT), `anitha.raj` (Cyber Security), `lakshmi.priya` (HR), `ramesh.kannan` (Talent Acquisition), `harish.prabhu` (App Development), `gokul.ravi` (Pre-Sales), `ajay.dev` (Graphic & Media) and `revathi.sundar` (Payroll). The full list is in `DevDataSeeder.java`.

The seeder also creates 3 projects and about 175 tasks, with due dates relative to today. This happens only when the tasks table is empty. The tasks give some people a deliberately heavy load: Karthik Raj, Priya Menon and Deepak Nair are overloaded, Arun Kumar and Sanjay Varma are high, and most others are low. That means the Overdue, Due today and Workload views have realistic content straight away.

When the tickets table is empty it also raises 19 help desk tickets across the categories, with times relative to now: some met, some at risk, two breached and one paused while waiting for the requester. Everyone who is assigned a seeded ticket (Vignesh, Deepak, Meena, Manoj, Karthik, Pooja, Arun) is granted `TICKET_EDIT`, which makes them a help desk agent for their department.

Phase 8 seed data (each part only while its table is empty): milestones, risks and members for the three projects (plus a few completed tasks linked to each, so progress is realistic); five approval requests in every state; four announcements (one asks for acknowledgement, one is scheduled); six published knowledge base articles; and three documents with several versions.

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
  - Tasks, workload, the dashboard and the calendar filter their queries through this scope.
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

## Dashboard, notifications & calendar (Phase 6)

- **`GET /api/dashboard`** (`DASHBOARD_VIEW`) serves every role from one endpoint, scoped on the server:
  - Super Admin: the whole company. Department manager: their departments plus their own assignments. Employee: their own assignments only, with no team or department sections.
  - KPIs, task status mix, weekly completions (8 weeks, with the on-time rate), department workload and performance, the busiest people, overdue and upcoming tasks, and recent task activity.
  - It is built from a few aggregate queries (`DashboardQuery`) plus the existing workload query, never per-row lookups.
  - Ticket, SLA and approval counts are `null` (shown as "—") until Phases 7 and 8 build those modules. The Digital Marketing summary arrives with the marketing dashboard (Phase 19).
- **Notifications** (`/api/notifications`, any signed-in user, always their own): a notification when someone else assigns you a task, plus a daily reminder for tasks due today/tomorrow and for overdue tasks. Reminders carry a dedup key (type, task, due date, assignee), so each is sent once. The topbar bell polls the unread count every minute.
- **Calendar** (`GET /api/calendar?from&to&mine`, `CALENDAR_VIEW`, at most 100 days): stored events merged at query time with the task deadlines the viewer can see. Event writes (`/api/calendar/events`, `CALENDAR_EDIT`): company-wide events need a Super Admin, department events need department management, and leave needs scope over the person. The calendar page itself arrives in Phase 8; the dashboard shows the next 14 days of events.

## Help desk & SLA (Phase 7)

- **Tickets** (`/api/tickets`, `TICKET_VIEW`): `TKT-000001` codes; search, filters (status, priority, category, team, view), sorting and paging. Anyone with `TICKET_CREATE` raises a ticket. The category decides the handling team (for "Other", the requester picks one).
- **Who sees what:**
  - Everyone sees the tickets they raised or are assigned.
  - Managers see their departments' tickets, and agents (`TICKET_EDIT`) see their own department's tickets.
  - Super Admin sees everything. Any other ticket returns 404.
- **Who does what:**
  - Agents change status, priority, category and team, and write **internal notes** that requesters never receive.
  - Managers (`TICKET_ASSIGN`) assign within their department and reopen closed tickets.
  - Requesters reply, and confirm (close) or reopen a resolved ticket.
- **SLA** (brief section 12): URGENT 1h/4h, HIGH 2h/8h, MEDIUM 4h/24h, LOW 8h/48h (first response / resolution).
  - **Due times:** set from the policy when the ticket is raised. Editing a policy only affects new tickets.
  - **States:** ON_TRACK, WARNING (from `sla.warningThresholdPct`, default 75% of the target used), BREACHED.
  - **First response:** the first public reply from anyone other than the requester. Resolving also counts.
  - **Paused clock:** the clock stops while the ticket waits for the requester, and between resolve and reopen. A requester reply while waiting resumes it.
  - **Compliance %:** met ÷ (met + missed) for tickets raised in the window; "—" when nothing is decided yet.
- **SLA page** (`/api/sla/summary`, `/api/sla/policies`): compliance, open tickets by state, tickets at risk, and per-priority targets. Targets can be edited by `SLA_MANAGE` holders; every change is versioned and audited.
- **Audit and notifications:** ticket create, assign and status changes are audited. Assignees are notified on assignment; requesters on status changes and agent replies; assignees on requester replies.

## Projects & collaboration (Phase 8)

- **Projects** (`/api/projects`, `PROJECT_VIEW`; changes need `PROJECT_EDIT`):
  - Progress is the share of non-cancelled tasks that are completed, unless a manual override is set ("—" when there are no tasks).
  - Projects have members, milestones (overdue when open and past due), risks (severity = probability × impact) and dependencies on other projects, with a loop check.
  - Visible to the project's department, its managers, the owner and members. Managers of the department and the owner can edit.
- **Approvals** (`/api/approvals`, `APPROVAL_VIEW`):
  - Seven request types, each with a configurable workflow (`APPROVAL_CONFIGURE`). A step is decided by the requester's department manager, by anyone holding a role, or by a named person.
  - On submit, the request gets its own copy of the steps, so later workflow changes don't affect it.
  - Nobody approves their own request. A manager's own request, or one from a department without a manager, escalates to a Super Admin.
  - Steps run in order; rejecting needs a reason; the requester can cancel while the request is pending.
  - Approvers are notified when a step reaches them, and requesters when the request is approved or rejected.
- **Announcements** (`/api/announcements`):
  - Addressed to everyone (Super Admin only) or to one department, with an optional publish time, expiry and acknowledgement.
  - Read and acknowledgement receipts are tracked per person. Managers see read counts.
- **Knowledge base** (`/api/knowledge-base`, `KB_VIEW`; writing needs `KB_EDIT`):
  - Markdown articles in the seeded categories, with tags and attachments. Raw HTML in an article is never rendered.
  - Search combines the MySQL FULLTEXT index (prefix matching) with a title match.
  - Drafts and archived articles are visible to editors only.
- **Documents** (`/api/documents`, `DOCUMENT_VIEW`; uploads need `DOCUMENT_EDIT`):
  - Every upload of a document becomes a new numbered version, and all versions stay downloadable.
  - Visible company-wide, within the owning department, or to the uploader.
- **Calendar** (`/my/calendar`): month, week and agenda views that combine events and leave, task deadlines, open project milestones and pending approval due dates. Events are added and edited in place (`CALENDAR_EDIT`).
