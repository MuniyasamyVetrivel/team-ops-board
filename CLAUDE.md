# CLAUDE.md: Team Ops Board

Internal Work & Performance Management System. Full requirements are in `docs/PROJECT_BRIEF.md` and the approved architecture, schema and phase plan are in `docs/implementation-plan.md`. Read both before starting a phase.

## Workflow
- Build **one phase at a time** in the order in `docs/implementation-plan.md`. Wait for the user's go-ahead before starting the next phase.
- After every phase:
  - Run `backend\mvnw.cmd verify` (unit + MVC slice tests, no DB) with **JDK 21** (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`). If the wrapper cannot write to `~/.m2/wrapper`, use the installed `mvn` (same 3.9.16).
  - When MySQL is available, also run `mvnw verify -Pit`, which runs the `*IT.java` integration tests against the real DB.
  - In `frontend/`, run `npm run typecheck`, `npm run lint`, `npm test` and `npm run build`. Builds must have no warnings.
  - Fix all compile, TypeScript and API errors. The app must stay runnable.
- Test layout:
  - `*Test.java` files are unit or slice tests that must not need a database.
  - `*IT.java` files are integration tests and only run with the `-Pit` profile.
  - Frontend tests sit next to the code they test, as `*.test.ts(x)` files.
- Before creating a component, service or file, check whether an equivalent already exists. Do not duplicate.
- Do not generate hundreds of files blindly. Build incrementally.

## Stack
- **Backend** (`backend/`):
  - Java 21, Spring Boot 4.1.1, Hibernate 7, Jackson 3 (`tools.jackson.*`, while annotations stay `com.fasterxml.jackson.annotation`), Maven via the `mvnw` wrapper.
  - Boot 4 uses modular starters (`spring-boot-starter-webmvc`, `-flyway`, `-security-oauth2-resource-server`, plus matching `*-test` starters). `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`. Use `@MockitoBean`, not `@MockBean`.
  - Spring Security with JWT (Spring's Nimbus encoder/decoder, HS256), plus a refresh token in an httpOnly cookie, hashed in the DB. BCrypt for passwords.
  - Spring Data JPA/Hibernate, Bean Validation, Lombok, MapStruct, springdoc-openapi.
  - Tests: JUnit 5, Mockito, Spring Boot Test.
- **Database**: MySQL 8 (`team_ops_board` on localhost:3306) with **Flyway** migrations in `backend/src/main/resources/db/migration`.
- **Frontend** (`frontend/`):
  - React 19, TypeScript 6.0 (strict), Vite 8 (Rolldown), React Router 8, Tailwind CSS v4, shadcn/ui-style components, Lucide v1, Recharts, React Hook Form + Zod 4, Axios, TanStack Query. Tests use Vitest + Testing Library.
  - Everything React Router needs is imported from `react-router`.
  - TypeScript stays on 6.0.x because typescript-eslint doesn't support 7 yet.
  - jsdom stays on 29 for Node 24.14 compatibility.
  - Lucide v1 renamed icons: use `LoaderCircle`, `ChartColumn`, `Building`. Brand icons such as LinkedIn were removed.
  - The navigation config in `src/config/navigation.ts` is the single source for the sidebar, the routes and the permission each route requires.
  - The access token is kept in memory only (`src/lib/api/client.ts`); never put tokens in localStorage.
- Package root: `com.teamops`. Organised by feature: `controller/ dto/ entity/ repository/ service/ mapper/`.

## Secrets & config
- Credentials live **only** in `backend/.env`, which is git-ignored and imported with `spring.config.import=optional:file:.env[.properties]`. Never hard-code or commit them.
- Keep `backend/.env.example` and `frontend/.env.example` up to date with placeholder values.
- Dev seed data comes from the Java `DevDataSeeder`, switched on with `DEV_SEED_ENABLED=true` (not a Spring profile, because `.env` can't activate profiles). Dates are relative to today, and the seeder must stay idempotent. Never put dev data in Flyway.
- The `dev` Spring profile only turns on verbose SQL and debug logging.

## Coding rules
- Controllers stay thin. Business logic goes in services, persistence in repositories.
- Use DTOs everywhere. **Never expose JPA entities** from controllers.
- Validate input with Bean Validation and Zod. Handle errors centrally in `GlobalExceptionHandler`.
- Avoid `any` in TypeScript. Write proper types for every API DTO.
- **Backend authorization** is enforced on three layers:
  - `@PreAuthorize` with permission authorities, e.g. `hasAuthority('TASK_EDIT')`. Roles are `ROLE_SUPER_ADMIN` etc. Inject the current user with `@AuthenticationPrincipal AuthenticatedUser`.
  - `AccessScopeService`, which applies department and own-data scoping to queries.
  - The current user, role, department and permissions are always resolved server-side. Never trust frontend claims, and hiding routes in the UI is not security.
- Flyway:
  - Never edit an applied migration. Add a new `V{n}__*.sql` instead.
  - `ddl-auto=validate`.
  - Ship each migration in the phase that first needs it.
- Schema conventions:
  - InnoDB with utf8mb4. **Signed `BIGINT` IDs**, mapped to `Long`. This changed from the plan's UNSIGNED, to avoid signedness mismatches on foreign keys.
  - `DATETIME(6)` timestamps in UTC, mapped to `Instant`. The JDBC URL forces the session time zone to UTC.
  - Money is `DECIMAL(14,2)` plus a `VARCHAR(3)` currency column, defaulting to INR.
- JPA mapping rules. Hibernate's `ddl-auto=validate` fails at startup if these are wrong:
  - Every enum field needs `@Enumerated(EnumType.STRING)` **and** `@JdbcTypeCode(SqlTypes.VARCHAR)`. Without the second one, Hibernate expects a MySQL `ENUM` column.
  - Don't use `CHAR(n)`; use `VARCHAR(n)`.
  - `TEXT`, `MEDIUMTEXT` and `JSON` columns need `@Column(columnDefinition = "text" | "mediumtext" | "json")`.
  - Extend `common.persistence.BaseEntity` (id, created_at, updated_at) when the table has both timestamps.
- **Do not store derived values** (rates, remaining, achievement %, statuses, workload %, SLA state, project progress). Compute them in services.
- Human-readable codes (`TSK-`, `TKT-`, `PRJ-`, `APR-`, `LEAD-`) come from the `code_sequences` table, read with `SELECT … FOR UPDATE`.
- Performance:
  - Paginate lists.
  - Avoid N+1 queries; use `@EntityGraph` or batch fetching.
  - Build dashboards from a few aggregate queries that return projections.
  - Add indexes for filter columns.
- Integration seams are interfaces with manual implementations for now: `FileStorageService`, `SeoRankingProvider`, `EmailCampaignProvider`, `PaidCampaignProvider`, `AnalyticsProvider`, `LeadProvider`, `ReportExporter`.
- File uploads must validate type, size and filename, and go through `FileStorageService`. Local storage for now.
- Throw `ApiException` (with a stable `code`) for business errors. Don't build error responses by hand.
- Write audit entries with `AuditService.record(...)`, which runs in its own transaction. Add new actions to the `AuditAction` enum.
- Write audit log entries for logins, task/ticket create/assign/status changes, target, ranking, campaign, lead and backlink changes, and permission changes.
- UI quality bar:
  - Should look like a modern enterprise SaaS product (Linear/Jira/HubSpot feel), not a Bootstrap template.
  - Every list or page needs skeleton, empty and error states.
  - **Never use colour alone** for status. Always pair it with a label or icon.

## Roles
- `SUPER_ADMIN` (Rakesh) has all permissions and sees everything.
- `DEPARTMENT_MANAGER` manages their own department.
- `EMPLOYEE` works on their own assigned work.
- The Digital Marketing menu and APIs are available only to SUPER_ADMIN and to users holding marketing permissions (`MARKETING_VIEW` etc.).

## Business rules (implement exactly; unit-test them)
- **Task statuses**: TODO, IN_PROGRESS, BLOCKED, IN_REVIEW, COMPLETED, CANCELLED.
- **Task priorities**: LOW, MEDIUM, HIGH, URGENT.
- **Ticket statuses**: NEW, OPEN, IN_PROGRESS, WAITING_FOR_REQUESTER, RESOLVED, CLOSED. Ticket codes look like `TKT-000001`.
- **Workload %**: remaining estimated hours of active tasks due in the window (default 14 days, overdue included) ÷ (weekly capacity × window weeks) × 100.
  - Remaining hours = max(estimated − actual, 0).
  - An unestimated task counts as the `default_task_hours` setting (default 4).
  - Levels: 0–40 LOW, 41–70 NORMAL, 71–100 HIGH, 101+ OVERLOADED.
- **SLA defaults** (first response / resolution):
  - URGENT 1h / 4h
  - HIGH 2h / 8h
  - MEDIUM 4h / 24h
  - LOW 8h / 48h
  - Due times are snapshotted at ticket creation. States: ON_TRACK, WARNING, BREACHED. The clock pauses while WAITING_FOR_REQUESTER.
- **SEO ranking**:
  - Positions 1–10 are GREEN, labelled "TOP 10".
  - Positions 11–100 are ORANGE, labelled "RANKING".
  - No position is RED, labelled "NOT RANKED" (stored as a NULL position).
  - Change = previous − current. Positive means improved (green up arrow), negative means declined (red down arrow), zero is no change (gray).
- **Ranking history** is insert-only per month.
  - Unique key: `keyword_id + ranking_month + ranking_year`.
  - Never overwrite earlier months. A same-month correction is an audited update.
- **Targets** are monthly, with unique key `target_type + month + year`. Never overwrite history.
  - Achievement % = actual ÷ target × 100.
  - Remaining = max(target − actual, 0).
  - Status: actual ≥ target is ACHIEVED (green). Achievement below the configurable threshold (default 60%, per-type override allowed) is BEHIND (red). Anything else is IN PROGRESS (orange).
- **Email**:
  - Open rate = unique opens ÷ delivered × 100.
  - Click rate = unique clicks ÷ delivered × 100.
  - Lead conversion = leads ÷ delivered × 100.
- **Paid campaigns**:
  - CTR = clicks ÷ impressions × 100.
  - CPL = spend ÷ leads.
  - Conversion rate = conversions ÷ leads × 100.
  - Remaining budget = budget − spent.
- **Backlinks**: track target, submitted, approved, live and remaining separately for each month.
- **Content**: track target, published, remaining and leads for each month.
- **Recurring activities** (DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY): completing an occurrence (or its generated task) creates the next occurrence and its task. Past occurrences stay in history. Unique key: activity + period_start.
- **CSV import**: validate first and show valid records, invalid records and errors. Never silently insert bad data.
- Division by zero gives `null`, which the UI shows as "—".
- Reference test values:
  - Position 7 → GREEN. Position 15 → ORANGE. NR → RED.
  - 250/200 → 80% with 50 remaining. 250/275 → 110% with 0 remaining.
  - Open rate 8,500/24,000 = 35.42%.
  - CPL ₹42,000/84 = ₹500.
