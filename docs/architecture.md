# Architecture

Team Ops Board is a single-company internal web application: a React single-page app talking JSON to a Spring Boot
REST API backed by one MySQL 8 database. The requirements are in [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) and the build
history and decisions in [`implementation-plan.md`](implementation-plan.md). Related references:
[database](database.md), [API](api.md), [Digital Marketing](digital-marketing.md).

```
 Browser ── React SPA (Vite build, static files)
    │  /api/** JSON, Authorization: Bearer <access JWT>, httpOnly refresh cookie on /api/auth
    ▼
 Spring Boot 4.1 (Java 21) ── Spring Security (JWT resource server) ── controllers ── services ── repositories
    │                                                                                      │
    │  Flyway migrations, Hibernate validate                                            JPA / JdbcTemplate
    ▼                                                                                      ▼
 MySQL 8 (team_ops_board, utf8mb4, UTC)                         local file storage (FileStorageService)
```

## Backend (`backend/`)

**Stack:** Java 21, Spring Boot 4.1 (modular starters), Spring Security with Spring's Nimbus JWT encoder/decoder
(HS256), Spring Data JPA on Hibernate 7, `NamedParameterJdbcTemplate` for aggregate queries, Flyway, Bean Validation,
Jackson 3, Lombok. Built with the Maven wrapper.

### Package layout (`com.teamops`)

Packages are organised by feature, each with `controller/ dto/ entity/ repository/ service/` as needed:

| Package | Responsibility |
|---|---|
| `auth` | Login, refresh-token rotation, logout, `/me` |
| `user`, `department`, `team` | People, roles and permissions, departments and memberships, the team directory and profiles |
| `task`, `workload` | Tasks (checklist, comments, attachments, watchers, dependencies, history) and the workload calculation |
| `ticket`, `sla` | Help desk tickets and SLA policies, clocks and compliance |
| `project`, `approval` | Projects (members, milestones, risks, dependencies) and approval workflows |
| `announcement`, `knowledge`, `document`, `calendar`, `notification`, `tag` | Collaboration modules |
| `dashboard`, `report`, `search` | Home dashboard, management reports with CSV export, global search |
| `marketing.*` | The Digital Marketing module (see [digital-marketing.md](digital-marketing.md)) |
| `admin` | Admin settings, the role permission matrix and the audit log viewer |
| `devdata` | `DevDataSeeder` and its module seeders (development only, `DEV_SEED_ENABLED=true`) |
| `common` | Cross-cutting pieces listed below |

### Cross-cutting building blocks (`common`)

| Piece | Purpose |
|---|---|
| `security.SecurityConfig`, `UserPrincipalService` | Stateless JWT security. Every request reloads the user, roles, department and permissions from the database, so JWT claims are never trusted |
| `security.AccessScopeService` | Data scope: `ALL` (Super Admin), `DEPARTMENTS` (a manager's departments) or `OWN`; `canViewWorkOf`, `canManageDepartment` |
| `exception.ApiException`, `GlobalExceptionHandler` | Business errors with a stable `code`; one error body for every failure |
| `web.PageRequests`, `PageResponse` | Paging with a sort-field whitelist per list (`page`, `size` ≤ 100, `sort=field,dir`) |
| `audit.AuditService`, `AuditChanges`, `AuditCatalog` | Audit entries written in their own transaction (`REQUIRES_NEW`), field-level `{from, to}` changes |
| `sequence.CodeGenerator` | Human-readable codes (`TSK-`, `TKT-`, `PRJ-`, `APR-`, `LEAD-`, `BLK-`) from `code_sequences` with `SELECT … FOR UPDATE` |
| `config.BusinessCalendar` | "Today" in `APP_TIME_ZONE` (default Asia/Kolkata); the server and browser clocks are never used for due dates |
| `storage.FileStorageService`, `FileService`, `UploadPolicy` | Upload validation (type, size, filename) and local storage behind an interface (S3 later) |
| `csv.CsvImportService`, `CsvImporter` | Preview-then-commit CSV imports, bound to the previewed file by checksum |
| `report.ReportDocument`, `ReportExporter` | Report export seam: CSV now, PDF later as another exporter |
| `settings.AppSettingsService` | Admin-editable settings with typed ranges and fallback defaults |

### Request flow

1. The resource server validates the HS256 access token, and `DatabaseJwtAuthenticationConverter` (through `UserPrincipalService`) loads the current
   user from the database (a disabled user is rejected on their next request).
2. URL rules in `SecurityConfig` (public auth endpoints, `/api/marketing/**` needs `MARKETING_VIEW`) and
   `@PreAuthorize("hasAuthority('…')")` on every controller method check permissions.
3. Thin controllers validate DTOs (Bean Validation) and call a service with `@AuthenticationPrincipal
   AuthenticatedUser`.
4. Services apply data scope (`AccessScopeService`, `TaskAccess`, `TaskWorkScope`) and business rules, and return
   DTOs built with static `of(...)` factories. JPA entities never leave the service layer.
5. Errors become `ApiException`s and are rendered by `GlobalExceptionHandler`. A record the viewer can't see answers
   404, not 403.

### Data access and performance

- Hibernate runs with `ddl-auto=validate`; Flyway owns the schema. `open-in-view` is off and
  `default_batch_fetch_size` is 50.
- Lists page in the database and filter with composable `Specification`s that use subqueries rather than collection
  joins, so paging stays correct. Associations needed by a list are loaded with `@EntityGraph` or batch fetching.
- Dashboards and reports are a few grouped `JdbcTemplate` queries returning projections. They never run one query per
  row. Derived values (rates, remaining, achievement %, statuses, workload %, SLA state, project progress) are
  computed in services, not stored.
- `QueryBudgetIT` (Phase 24) guards this. It runs every dashboard, report, list and detail endpoint against the
  database and fails when an endpoint exceeds its statement budget, runs one statement more than three times (an
  N+1), or scans a growing table without any usable index.
- Editable records carry `@Version`. Clients send `version`, and a conflict answers 409 `STALE_UPDATE`.

### Background jobs

Both run in `APP_TIME_ZONE` and can be switched off with `APP_SCHEDULING_ENABLED=false`:

| Job | Default schedule | What it does |
|---|---|---|
| `TaskReminderService` | `0 0 8 * * *` (`TASK_REMINDER_CRON`) | Due-soon and overdue task reminders, deduplicated per task, due date and person |
| `ActivityScheduler` | `0 10 0 * * *` (`MARKETING_ACTIVITY_CRON`) | Creates the current period's recurring-activity occurrences and their tasks |

### Integration seams

External systems are interfaces with manual implementations for now. An integration replaces one by registering a
`@Primary` bean: `SeoRankingProvider`, `EmailCampaignProvider`, `PaidCampaignProvider`, `AnalyticsProvider`,
`LeadProvider`, `FileStorageService` and `ReportExporter`.

## Authentication and authorization

- **Login:** email and password are checked against a BCrypt hash (strength 12). The response carries a short-lived
  access JWT (default 60 minutes) and sets an httpOnly, `SameSite=Strict` refresh cookie scoped to `/api/auth`.
- **Refresh tokens** rotate on every use and only their SHA-256 hash is stored. Re-using a rotated token (outside a
  10-second grace window for concurrent tabs) revokes all of that user's sessions and is audited.
- **The frontend keeps the access token in memory only**, never in `localStorage`. On load it restores the session
  through `POST /api/auth/refresh`; on a 401 it refreshes once and retries.
- **Roles:** `SUPER_ADMIN` (every permission), `DEPARTMENT_MANAGER`, `EMPLOYEE`. Permissions (`TASK_EDIT`,
  `SEO_VIEW`, …) are Spring authorities. A role's permissions change only through `RolePermissionService`.
  Digital Marketing permissions are never put on a role; they are granted per person.
- **Three layers:**
  1. `@PreAuthorize` permission checks on every endpoint.
  2. Data scope in queries (`AccessScopeService`): Super Admin sees everything. A manager sees their primary
     department, departments where they are `manager_id`, and departments where they are a MANAGER member. Everyone
     else sees their own work.
  3. Service rules, such as the no-privilege-escalation rules in `UserService`: you can only grant permissions you
     hold; only a Super Admin can grant, disable or demote a Super Admin; nobody changes their own access or disables
     themselves; and the last active Super Admin can't be removed.
- Hiding a route or menu in the UI is a convenience, not security. The backend enforces every rule.

## Frontend (`frontend/`)

**Stack:** React 19, TypeScript 6 (strict), Vite 8 (Rolldown), React Router 8, Tailwind CSS 4 with shadcn/ui-style
components, Lucide icons, Recharts, React Hook Form + Zod 4, Axios and TanStack Query.

| Folder | Contents |
|---|---|
| `src/config/navigation.ts` | The single source for the sidebar, the routes and the permission each route requires |
| `src/routes/router.tsx` | Lazy routes (`IMPLEMENTED_PAGES`, `detailRoutes`) with permission guards |
| `src/layouts/` | App shell: sidebar, top bar (global search, notifications, theme menu, user menu) |
| `src/features/<module>/` | Pages, drawers and dialogs per module, plus `api.ts` (DTO types that mirror the Java records, query keys and TanStack Query hooks) |
| `src/components/ui/` | Base components (`Dialog`, `SheetContent`, `Tabs`, `Table`, `Badge`, …) |
| `src/components/common/` | Shared pieces: `StatusBadge`, `UserCell`, `Pagination`, `SearchInput`, `ErrorState`, `EmptyState`, `FormField`, `PageHeader` |
| `src/lib/` | API client (in-memory access token, refresh-once on 401), formatting, theme, query client |
| `src/test/` | `renderPage(...)` + `mockApi(...)` helpers: page tests use the real query hooks with a fake HTTP adapter |

- **Data:** each `features/*/api.ts` exposes query hooks. Mutations update the detail cache and invalidate the
  related lists. Queries are cached for 30 seconds and don't refetch on window focus.
- **Forms:** React Hook Form with Zod schemas that mirror the backend validation. Server field errors are mapped back
  with `applyServerErrors`.
- **Dates:** the server sends `dueState`, SLA state and other time-based statuses. The UI renders them and parses
  dates with `parseLocalDate`; it never decides "overdue" from the browser clock.
- **Status display:** semantic tokens (success / warning / danger / neutral) in light and dark themes, always paired
  with a label or icon, never colour alone.
- **Code splitting:**
  - Every page is a lazy route.
  - Vendor code is split into long-lived chunks (react, router, ui, data, forms).
  - Recharts and the Markdown pipeline get their own chunks, loaded only by the pages that use them.
  - The first load fetches about 205 kB gzip of JavaScript.

## Testing

| Layer | Where | Runs with |
|---|---|---|
| Unit and MVC slice tests (`*Test.java`) | No database. Slice tests use `@WebMvcTest` + `@SecuritySliceTest` and mint tokens with `SliceAuth` | `mvnw verify` |
| Integration tests (`*IT.java`) | Real MySQL, Flyway and security. Each test uses throwaway users (`IntegrationUsers`) and usually its own department | `mvnw verify -Pit` |
| Frontend (`*.test.ts(x)`) | Vitest + Testing Library, next to the code | `npm test` |

Every brief section 86 case has an automated test (Phase 23 in
[`implementation-plan.md`](implementation-plan.md)). `QueryBudgetIT` guards query counts and indexes.
