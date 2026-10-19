# Architecture

Team Ops Board is a single-company internal web application: a React single-page app talking JSON to a Spring Boot
REST API backed by one MySQL 8 database. The requirements are in [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) and the build
history and decisions in [`implementation-plan.md`](implementation-plan.md); the expansion (X1–X8) is specified in
[`EXPANSION_BRIEF.md`](EXPANSION_BRIEF.md). Related references: [database](database.md), [API](api.md),
[Digital Marketing](digital-marketing.md), [backups](backup-and-restore.md), [adding a module](adding-a-module.md).

```
 Browser ── React SPA (Vite build, static files)
    │  /api/** JSON, Authorization: Bearer <access JWT>, httpOnly refresh cookie on /api/auth
    ▼
 Spring Boot 4.1 (Java 21) ── Spring Security (JWT resource server) ── controllers ── services ── repositories
    │                                                                                      │
    │  Flyway migrations, Hibernate validate                                            JPA / JdbcTemplate
    ▼                                                                                      ▼
 MySQL 8 (team_ops_board, utf8mb4, UTC)                         local file storage (FileStorageService)
    │                                                                                      │
    └──── nightly mysqldump + uploads ── BackupCrypto (AES-256-GCM) ── BackupTarget (local folder or S3, optional 2nd copy)
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
| `user`, `department`, `team` | People, roles and permissions, departments and memberships, the team directory and profiles; employee IDs and profile photos (`ProfilePhotoService`, `ProfilePhotoImages`); break-glass recovery from the command line (`BreakGlassService`, `BreakGlassRunner`, no HTTP endpoint) |
| `task`, `workload` | Tasks (checklist, comments, attachments, watchers, dependencies, history) and the workload calculation |
| `ticket`, `sla` | Help desk tickets and SLA policies, clocks and compliance |
| `project`, `approval` | Projects (members, milestones, risks, dependencies) and approval workflows |
| `announcement`, `knowledge`, `document`, `calendar`, `notification`, `tag` | Collaboration modules |
| `celebration` | Employees (linked to app users by employee ID, `EmployeeUserLinks`), birthdays and work anniversaries, important days, events, LinkedIn posts and their reminders |
| `dashboard`, `report`, `search` | Home dashboard, management reports with CSV export, global search |
| `marketing.*` | The Digital Marketing module (see [digital-marketing.md](digital-marketing.md)) |
| `recyclebin` | The recycle bin: listing, restore, permanent delete and the nightly purge over soft-deleted records (`RecycleBinService`; module rules in `RecycleBinHandler` beans) |
| `admin`, `history` | Admin settings, the role permission matrix, the audit log viewer, and each record's History tab (`RecordHistoryAccess` reuses the module's read check) |
| `backup` | Nightly encrypted backups (`BackupService`, `BackupCrypto`, `BackupRetention`, `BackupHealth`, `BackupTargets`), the Backups page API and "Export all data" (`DataExportService`, with `FinanceRedaction`); see [backup-and-restore.md](backup-and-restore.md) |
| `module` | Department tools framework: `ModuleDefinition` beans, the per-department switch (`department_modules`), `ModuleAccessService` and `ModuleAccessInterceptor`; see [adding-a-module.md](adding-a-module.md) |
| `bench`, `vendor`, `finance` | The department tools: Bench Management, Vendor Management and the Finance Tracker, each under `/api/modules/{key}/` |
| `devdata` (test sources) | `DevDataSeeder` and its module seeders, for automated tests only; never part of the application |
| `common` | Cross-cutting pieces listed below |

### Cross-cutting building blocks (`common`)

| Piece | Purpose |
|---|---|
| `security.SecurityConfig`, `UserPrincipalService` | Stateless JWT security. Every request reloads the user, roles, department and permissions from the database, so JWT claims are never trusted |
| `security.AccessScopeService` | Data scope: `ALL` (Super Admin), `DEPARTMENTS` (a manager's departments) or `OWN`; `canViewWorkOf`, `canManageDepartment` |
| `exception.ApiException`, `GlobalExceptionHandler` | Business errors with a stable `code`; one error body for every failure |
| `web.PageRequests`, `PageResponse` | Paging with a sort-field whitelist per list (`page`, `size` ≤ 100, `sort=field,dir`) |
| `audit.AuditService`, `AuditChanges`, `AuditCatalog` | Business audit events written in their own transaction (`REQUIRES_NEW`), field-level `{from, to}` changes |
| `audit.EntityChangeAuditor`, `persistence.AuditedEntity` | Record history for every entity: Hibernate insert/update/delete (and many-to-many) events become `RECORD_*` entries with a field diff, collected per transaction and written just before commit on the same connection (rolled-back changes leave nothing). Stamps `created_by`/`updated_by` with the signed-in user (`@CurrentActor`). `@AuditIgnore`, `@AuditFinance`, `@AuditType` and `AuditedChild` (parent record) tune it per entity; secrets are redacted by name |
| `sequence.CodeGenerator` | Human-readable codes (`TSK-`, `TKT-`, `PRJ-`, `APR-`, `LEAD-`, `BLK-`) from `code_sequences` with `SELECT … FOR UPDATE` |
| `config.BusinessCalendar` | "Today" in `APP_TIME_ZONE` (default Asia/Kolkata); the server and browser clocks are never used for due dates |
| `storage.FileStorageService`, `FileService`, `UploadPolicy` | Upload validation (type, size, filename) and local storage behind an interface (S3 later) |
| `csv.CsvImportService`, `CsvImporter` | Preview-then-commit CSV imports, bound to the previewed file by checksum |
| `report.ReportDocument`, `ReportExporter` | Report export seam: CSV now, PDF later as another exporter |
| `settings.AppSettingsService`, `SettingChoices` | Admin-editable settings with typed ranges and fallback defaults; a CHOICE setting takes one of the values a `SettingChoices` bean lists |
| `persistence.SoftDeletableEntity`, `recyclebin.RecycleBinService` | Soft delete: deleted records keep their row (`deleted_at`, `deleted_by`) and are hidden by `@SQLRestriction` until restored or purged |
| `security.FinanceAccess` | Whether the viewer may see finance data (`FINANCE_VIEW`); used by vendor contracts, history, the audit log, the recycle bin, search and "Export all data" |
| `security.PasswordChangeRequiredFilter`, `PasswordPolicy` | A user with an admin-set password may only call `/api/auth/me` and `/api/auth/change-password` until they choose their own |

### Request flow

1. The resource server validates the HS256 access token, and `DatabaseJwtAuthenticationConverter` (through `UserPrincipalService`) loads the current
   user from the database (a disabled user is rejected on their next request).
2. URL rules in `SecurityConfig` (public auth endpoints, `/api/marketing/**` needs `MARKETING_VIEW`) and
   `@PreAuthorize("hasAuthority('…')")` on every controller method check permissions. Under `/api/modules/{key}/`,
   `ModuleAccessInterceptor` also checks that the user holds the tool's view permission (GET/HEAD) or edit permission
   (anything else), 403 `FORBIDDEN` otherwise, and that the tool is switched on for one of their departments, 403
   `MODULE_DISABLED` otherwise (404 `MODULE_NOT_FOUND` for an unknown key).
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

All run in `APP_TIME_ZONE` and can be switched off with `APP_SCHEDULING_ENABLED=false` (the backup also with
`BACKUP_ENABLED=false`):

| Job | Default schedule | What it does |
|---|---|---|
| `TaskReminderService` | `0 0 8 * * *` (`TASK_REMINDER_CRON`) | Due-soon and overdue task reminders, deduplicated per task, due date and person |
| `ActivityScheduler` | `0 10 0 * * *` (`MARKETING_ACTIVITY_CRON`) | Creates the current period's recurring-activity occurrences and their tasks |
| `CelebrationReminderScheduler` | `0 0 8 * * *` (`CELEBRATION_REMINDER_CRON`) | Birthday, work anniversary, important day and event reminders, the Admin Settings number of days before |
| `BenchReminderScheduler` | `0 15 8 * * *` (`BENCH_REMINDER_CRON`) | Roll-off reminders 30 and 14 days before an allocation ends, to `BENCH_VIEW` holders |
| `VendorReminderScheduler` | `0 20 8 * * *` (`VENDOR_REMINDER_CRON`) | Contract renewal reminders 30 and 7 days before, to `VENDOR_VIEW` holders |
| `FinanceReminderScheduler` | `0 25 8 * * *` (`FINANCE_REMINDER_CRON`) | Licence renewal reminders 30 and 7 days before (active and trial licences), to `FINANCE_VIEW` holders |
| `RecycleBinPurgeScheduler` | `0 30 2 * * *` (`RECYCLE_BIN_PURGE_CRON`) | Permanently deletes recycle bin items older than the retention setting (default 90 days) |
| `BackupScheduler` | `0 0 2 * * *` (`BACKUP_CRON`) | Encrypted backup of the database and uploads, verified on every copy, then retention |

Tool reminders go to `ModuleAccessService.recipients(key)`: people who hold the tool's view permission and may open it.

### Integration seams

External systems are interfaces with manual implementations for now. An integration replaces one by registering a
`@Primary` bean: `SeoRankingProvider`, `EmailCampaignProvider`, `PaidCampaignProvider`, `AnalyticsProvider`,
`LeadProvider`, `FileStorageService` and `ReportExporter`. `BackupTarget` has a local-folder and an S3 implementation
(Amazon S3 or Lightsail object storage), chosen by `BACKUP_TARGET` and `BACKUP_SECONDARY_TARGET`. Inside the app,
`VendorLinkSource` beans (licences, expenses) add sections to the vendor page and block deleting a vendor in use.

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
- **Expansion features are Super Admin only by default.** History (`HISTORY_VIEW`), Audit Logs (`AUDIT_VIEW`),
  Recycle Bin (`RECYCLE_BIN_MANAGE`, `RECYCLE_BIN_PURGE`), Backups (`BACKUP_VIEW`), Export (`DATA_EXPORT`), Modules
  (`MODULE_MANAGE`) and the tools (`BENCH_*`, `VENDOR_*`, `FINANCE_*`) each have their own permission, seeded on
  `SUPER_ADMIN` only; the Super Admin can grant them later. Employee IDs and photos (X4) are for everyone.
- **Finance data is hidden without `FINANCE_VIEW`** (`FinanceAccess`): vendor contract values read as hidden, finance
  records' audit entries carry no details or name, finance recycle bin items have no name, search leaves out the
  finance groups, and "Export all data" leaves out finance tables and receipts and redacts finance columns.
- Hiding a route or menu in the UI is a convenience, not security. The backend enforces every rule.

## Frontend (`frontend/`)

**Stack:** React 19, TypeScript 6 (strict), Vite 8 (Rolldown), React Router 8, Tailwind CSS 4 with shadcn/ui-style
components, Lucide icons, Recharts, React Hook Form + Zod 4, Axios and TanStack Query.

| Folder | Contents |
|---|---|
| `src/config/navigation.ts` | The single source for the sidebar, the routes and the permission each route requires |
| `src/routes/router.tsx` | Lazy routes (`IMPLEMENTED_PAGES`, `detailRoutes`) with permission guards |
| `src/layouts/` | App shell: sidebar, top bar (global search, notifications, theme menu, user menu) |
| `src/features/<module>/` | Pages, drawers and dialogs per module, plus `api.ts` (DTO types that mirror the Java records, query keys and TanStack Query hooks). Shared expansion pieces: `history` (`RecordHistory`, `HistoryPanel`), `recycle-bin` (undo toast), `profile-photo`, `modules` (`ToolLayout` for the department tools in `bench`, `vendors`, `finance`) |
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
| Integration tests (`*IT.java`) | Real MySQL in its own database, `team_ops_board_it` (never the real one), with Flyway and security. Each test uses throwaway users (`IntegrationUsers`) and usually its own department | `mvnw verify -Pit` |
| Frontend (`*.test.ts(x)`) | Vitest + Testing Library, next to the code | `npm test` |

Every brief section 86 case has an automated test (Phase 23 in
[`implementation-plan.md`](implementation-plan.md)). `QueryBudgetIT` guards query counts and indexes.
