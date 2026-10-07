# Team Ops Board: Phase 1 (Repo Inspection) + Phase 2 (Architecture & DB Design)

## Context
`docs/PROJECT_BRIEF.md` specifies an "Internal Work & Performance Management System" (tasks, workload, help desk, projects, approvals, collaboration, reports, admin, and a large Digital Marketing performance module) so Rakesh, the Super Admin, can see team workload and marketing performance at a glance. The user asked for **Phase 1 + Phase 2 only**: confirm the repo state, then propose folder structure, the full MySQL schema with Flyway migration order, and a phase-by-phase plan. **No application code is written in this step.** Constraints: Java 21, latest stable Spring Boot, MySQL on `localhost:3306` (db `team_ops_board`, user `teamops`). Credentials live in `.env` only and are never hard-coded or committed.

---

## Progress & decisions changed during implementation

| Phase | Status |
|---|---|
| 1–2 | Done: this document |
| 3 Authentication | Done and verified against MySQL |
| 5 Tasks + Workload | Done: V3 projects + V4 tasks; task CRUD, assign, status with reopen, comments, checklist, watchers, dependencies with cycle check, tags, attachments, history, audit; My Tasks; the Workload page; real work data on team profiles; 175-task seed. 170 unit and slice tests plus 10 integration tests pass against MySQL |
| 6 Dashboard | Done: V5 notifications + calendar_events; role-scoped `/api/dashboard` (KPIs, status donut, weekly completion, department workload/performance, employee workload, overdue/upcoming, recent activity); notifications with assignment events and daily due/overdue reminders; calendar API (events + task deadlines) with scoped event CRUD; topbar bell. 198 unit and slice tests plus 13 integration tests pass against MySQL |
| 7 Tickets + SLA | Done: V6 sla_policies (seeded), ticket_categories (seeded, routed to a team), tickets with snapshotted SLA, comments with internal notes, attachments, history; ticket API with scope rules and audit; SlaCalculator (warning, breach, paused clock, compliance); SLA summary and policy admin; dashboard ticket KPIs; Tickets, My Tickets and SLA pages; 19-ticket seed. 242 unit and slice tests plus 17 integration tests pass against MySQL; 78 frontend tests |
| 4 Users / Departments / Roles | Done: user admin (create, edit, access, disable, reset password), departments and secondary members, `AccessScopeService`, team directory and profile, 22-user seeder. 105 unit and slice tests plus 7 integration tests pass against MySQL |

These decisions supersede the plan text below:
- **Versions:** Spring Boot **4.1.1** (latest GA; 4.2.0 is still at milestone 2), TypeScript **6.0.x** (typescript-eslint doesn't support 7 yet), React Router **8**, Vite **8**, jsdom **29** (30 needs Node 24.15+).
- **IDs** are signed `BIGINT`, not `BIGINT UNSIGNED`. Signed ids map to Java `Long` and avoid signedness mismatches on foreign keys.
- **Dev seed data** is switched on with `DEV_SEED_ENABLED=true` in `.env`, not with a `dev` Spring profile, because a `.env` property can't activate profiles. The `dev` profile only adds verbose logging.
- **Integration tests** use the local MySQL, run with `mvnw verify -Pit` (`*IT.java`). Testcontainers was not added.
- **Schema/entity mapping rules** (enum `VARCHAR`, no `CHAR`, `columnDefinition` for `TEXT`/`JSON`) are recorded in `CLAUDE.md`.
- **MapStruct was not added.** DTOs use small static `of(...)` factories, which are simpler at this size.
- **The user admin UI assigns one role per user** (Super Admin, Department Manager or Employee), plus direct permission grants. The backend still accepts a set of roles.
- **Workload includes undated open tasks** (as well as overdue tasks and tasks due within the window), so undated work can't hide an overload. Status, priority and date filters only narrow the count columns. The Completed column defaults to the last 30 days.
- **"Today" is evaluated in a business time zone** (`APP_TIME_ZONE`, default Asia/Kolkata), and the server sends each task's `dueState`.
- **`projects.progress_override` is `INT`** rather than `TINYINT`, which keeps schema validation simple; a CHECK constraint keeps it within 0–100.
- **Every sort puts empty values last** (`PageRequests`), for example undated tasks.
- **No new migrations in Phase 4.** V1 already created `department_members`, `user_permissions` and the other identity tables.
- **Phase 6 dashboard is one endpoint for every role**, scoped by `AccessScope`; dashboard counts use the viewer's *work* (`TaskSpecifications.workOf`: managed departments plus own assignments), not every task they can open. Ticket/SLA/approval KPIs are `null` until their phases; the marketing summary waits for Phase 19.
- **Recent activity comes from `task_history`** (scoped through the task), not `audit_logs`, so managers and employees get a meaningful, permission-safe feed.
- **`notifications.dedup_key`** (unique per user) makes the daily reminder job idempotent. `calendar_events` gained `version` (optimistic locking) and stores all-day events as business-zone midnights.
- **The calendar API ships in Phase 6 with task deadlines and events only.** Project milestones and approval deadlines join it in Phase 8 with their modules.
- **SLA warning threshold is global** (`sla.warningThresholdPct` in `app_settings`), not per policy, and is snapshotted on each ticket with the due times (`sla_warning_pct`). `sla_policies` has one row per priority and no `active` flag. Tickets store an explicit `sla_start_at`.
- **SLA states are ON_TRACK / WARNING / BREACHED per deadline**, plus `met` (null while open) and `paused` flags. The SLA clock also pauses between resolve and reopen, so time spent resolved never counts. A priority change does not move due times.
- **Tickets are routed to a handling department** by category (`ticket_categories.default_department_id`). Help desk agents are users with `TICKET_EDIT` in that department; employees do not get it by role.
- **Ticket search uses LIKE on subject and code**, consistent with tasks; the planned FULLTEXT index was not added. SLA figures are computed in Java with `SlaCalculator` over the viewer's tickets, so the list, SLA page and dashboard always agree.

---

## Phase 1: Repository state (confirmed)

| Area | Finding |
|---|---|
| Files | Only `docs/PROJECT_BRIEF.md` and `docs/ui-reference/` (2 Freshdesk-style dark-theme screenshots: a KPI-card dashboard and a ticket list with a filter side panel) |
| Frontend / backend | None. Nothing to reuse, so this is a **greenfield** build |
| DB config / APIs / auth / components | None |
| Git | **Not a git repository** |
| Java | `java` on PATH and `JAVA_HOME` point to **JDK 8**. **JDK 21 is installed** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| Maven | 3.9.16 |
| Node / npm | v24.14.1 / 11.11.0 |
| MySQL | Port 3306 is reachable. `mysql` CLI is not on PATH, so credentials get verified by the first Flyway run in Phase 3 |

**Consequences**
- Every Maven build must run with `JAVA_HOME` set to JDK 21. The plan adds the Maven Wrapper (`mvnw`), `maven-enforcer-plugin` (requireJavaVersion 21) so a wrong JDK fails fast, and a short README note. Recommendation: the user switches the system `JAVA_HOME` to JDK 21.
- Run `git init` at the start of Phase 3, with a `.gitignore` that excludes `.env`, `uploads/`, `target/`, `node_modules/` and `dist/`.

UI direction from the screenshots: compact KPI tiles, a dense list with inline priority/assignee/status controls, an overdue badge, and a right-hand filter drawer. We take the patterns but not the look. The default theme is light with a dark-mode toggle, plus a Linear-style sidebar.

---

## Phase 2a: Technology decisions

**Backend**: Java 21 and **Spring Boot 4.x, latest GA**, with the exact patch confirmed on start.spring.io / Maven Central when Phase 3 scaffolds the project. Maven with the wrapper.
- Starters: web, security, **oauth2-resource-server** (Spring Security's built-in Nimbus `JwtEncoder`/`JwtDecoder`, HS256, so no third-party JWT library), data-jpa, validation, **flyway** (`spring-boot-starter-flyway` is a separate starter in Boot 4) + `flyway-mysql`, actuator. Plus `mysql-connector-j`, Lombok, MapStruct (entity→DTO), springdoc-openapi, and Apache Commons CSV.
- Auth: a short-lived access JWT (60 min) plus a **refresh token** in an httpOnly cookie, stored hashed in `refresh_tokens`. This gives real logout/revocation and session expiry. Passwords use BCrypt with strength 12.
- Authorization works on three layers:
  1. `@PreAuthorize` with permission authorities such as `TASK_EDIT` and `SEO_EDIT`.
  2. An `AccessScopeService` that turns the current user into a data scope: ALL for Super Admin, the department IDs a manager runs, or OWN for employees. Repository Specifications and queries apply that scope.
  3. The backend computes the user, role, department and permissions from the DB on every request. JWT claims are only a cache that gets re-validated.
- Env loading: `spring.config.import=optional:file:.env[.properties]` in `application.yml`. Boot reads `backend/.env` natively, with no extra dependency. Properties look like `spring.datasource.password=${DB_PASSWORD}`.
- Profiles: `dev` turns on the dev seeder and verbose SQL. `test` uses Testcontainers MySQL, falling back to the local DB if Docker is missing. `prod` is the default.
- JPA: `ddl-auto=validate` (Flyway owns the schema). `open-in-view=false`. UTC everywhere (`hibernate.jdbc.time_zone=UTC`). Batch fetching and `@EntityGraph` prevent N+1 queries. Dashboards use dedicated aggregate native/JPQL queries that return projections.
- Integration seams (interfaces now, manual implementations only): `FileStorageService` (`LocalFileStorageService`, with S3/SharePoint later), `SeoRankingProvider` (`ManualSeoRankingProvider`), `EmailCampaignProvider`, `PaidCampaignProvider`, `AnalyticsProvider`, `LeadProvider`, and `ReportExporter` (CSV now, PDF later).

**Frontend**: React 19, TypeScript (strict), Vite, React Router, Tailwind CSS v4, shadcn/ui (Radix), Lucide, Recharts, React Hook Form + Zod, Axios (an interceptor attaches the token and handles silent refresh on 401), TanStack Query, and sonner toasts. Every route is lazy-loaded. Tests use Vitest + Testing Library. `frontend/.env` holds only `VITE_API_BASE_URL`, which is not a secret.

---

## Phase 2b: Folder structure

```
team-ops-board/
├─ README.md   .gitignore
├─ docs/  PROJECT_BRIEF.md  architecture.md  database.md  api.md  digital-marketing.md  ui-reference/
├─ backend/
│  ├─ .env (git-ignored)  .env.example  pom.xml  mvnw  mvnw.cmd  .mvn/
│  └─ src/
│     ├─ main/java/com/teamops/
│     │  ├─ TeamOpsApplication.java
│     │  ├─ common/          # config/, security/ (JWT, filters, AccessScopeService, CurrentUser),
│     │  │                   # exception/ (GlobalExceptionHandler, ApiError), web/ (PageResponse),
│     │  │                   # audit/ (AuditService, @Audited aspect), storage/ (FileStorageService),
│     │  │                   # csv/ (CsvImportService, ImportResult), sequence/ (CodeGenerator), settings/
│     │  ├─ auth/  user/  department/  task/  workload/  project/  ticket/  sla/  approval/
│     │  ├─ announcement/  knowledge/  document/  team/  calendar/  notification/  search/
│     │  ├─ dashboard/  report/  admin/
│     │  ├─ marketing/
│     │  │  ├─ seo/ (pages, keywords, rankings, provider/)  target/  activity/  email/  paid/
│     │  │  ├─ lead/  backlink/  content/  dashboard/  report/  integration/ (provider interfaces)
│     │  └─ devdata/         # DevDataSeeder (@Profile("dev"))
│     │  # each feature: controller/ dto/ entity/ repository/ service/ mapper/
│     ├─ main/resources/ application.yml  application-dev.yml  db/migration/V*.sql
│     └─ test/java/com/teamops/... (unit + @SpringBootTest/@WebMvcTest)
└─ frontend/
   ├─ .env.example  package.json  vite.config.ts  tsconfig*.json  components.json  index.html
   └─ src/
      ├─ main.tsx  App.tsx  routes/ (router, ProtectedRoute, RoleRoute)
      ├─ lib/ (api client, query client, utils, formatters, status-colors)
      ├─ components/ui/ (shadcn)   components/common/ (KpiCard, StatusBadge, PriorityIndicator,
      │                             DataTable, FilterDrawer, EmptyState, ErrorState, PageHeader, UserAvatar)
      ├─ layouts/ (AppShell, Sidebar, Topbar)
      ├─ features/ auth/ dashboard/ my-work/ tasks/ workload/ projects/ tickets/ sla/ approvals/
      │            announcements/ knowledge/ documents/ team/ calendar/ reports/ admin/
      │            marketing/ dashboard/ pages/ keywords/ rankings/ targets/ activities/
      │                       email-campaigns/ paid-campaigns/ leads/ backlinks/ content/ reports/
      │                       components/ (MarketingKpiCard, RankingBadge, RankingTrend, TargetProgress, …)
      ├─ hooks/  types/ (API DTO types)  styles/
```

---

## Phase 2c: MySQL schema

**Conventions**: InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`. PK `id BIGINT UNSIGNED AUTO_INCREMENT`. `created_at`/`updated_at DATETIME(6)` in UTC. Enums are stored as `VARCHAR(32)` and mapped with `@Enumerated(STRING)`, so adding a value needs no `ALTER`. Monetary values use `DECIMAL(14,2)` with `currency CHAR(3) DEFAULT 'INR'`. `version INT` is an optimistic lock on tasks, tickets, targets and approvals. Every FK column and every common filter column gets an index. **Derived values are never stored**: remaining, achievement %, target status, ranking colour, open/click/CTR/CPL rates, workload %, SLA state and project progress are all computed in services.

Human-readable codes (`TSK-000001`, `TKT-000001`, `PRJ-0001`, `APR-000001`, `LEAD-000001`) come from the `code_sequences` table, using `SELECT … FOR UPDATE` in the same transaction so codes never collide.

### Identity & organisation
- **departments**: name (UQ), code (UQ), description, manager_id → users (nullable; FK added after users exist), status ACTIVE/INACTIVE, timestamps
- **users**: email (UQ), password_hash, first_name, last_name, job_title, phone, location, working_hours, avatar_file_id, department_id → departments (primary dept, NOT NULL), reports_to_id → users, weekly_capacity_hours DECIMAL(5,2) DEFAULT 40, status ACTIVE/DISABLED, last_login_at, timestamps
- **roles**: code (UQ: SUPER_ADMIN, DEPARTMENT_MANAGER, EMPLOYEE), name, description
- **permissions**: code (UQ), name, module, description
- **role_permissions**: (role_id, permission_id) PK
- **user_roles**: (user_id, role_id) PK
- **user_permissions**: (user_id, permission_id) PK, granted_by, granted_at. These are extra grants, for example `MARKETING_VIEW` for Digital Marketing staff. Effective permissions = role ∪ user grants.
- **department_members**: (department_id, user_id) PK, member_role MANAGER/MEMBER, joined_at. Holds secondary memberships and cross-department access. The primary department stays on `users`.
- **refresh_tokens**: user_id, token_hash (UQ), expires_at, revoked_at, created_at, user_agent, ip
- **app_settings**: setting_key (UQ), setting_value, value_type, description, updated_by, updated_at. Holds the target "behind" threshold, default hours for an unestimated task, the workload window, and upload limits.
- **code_sequences**: name (PK), prefix, pad_length, next_value

### Shared
- **files**: storage_provider (LOCAL/S3/…), storage_key (UQ), original_name, content_type, size_bytes, checksum_sha256, uploaded_by, created_at. All attachments and document versions point here.
- **tags**: name (UQ), color

### Projects
- **projects**: code (UQ), name, description, owner_id, department_id, start_date, end_date, status PLANNING/ACTIVE/ON_HOLD/COMPLETED/CANCELLED, progress_override TINYINT NULL (progress is otherwise computed from tasks), timestamps, version
- **project_members**: (project_id, user_id) PK, project_role, added_at
- **project_milestones**: project_id, name, description, due_date, completed_at, status, position
- **project_risks**: project_id, title, description, probability, impact, mitigation, owner_id, status
- **project_dependencies**: project_id, depends_on_project_id, UQ(pair)
- **project_attachments**: (project_id, file_id) PK

### Tasks
- **tasks**: code (UQ), title, description TEXT, department_id, project_id NULL, milestone_id NULL, assignee_id NULL, created_by, priority LOW/MEDIUM/HIGH/URGENT, status TODO/IN_PROGRESS/BLOCKED/IN_REVIEW/COMPLETED/CANCELLED, start_date, due_date, estimated_hours, actual_hours, completed_at, source MANUAL/MARKETING_ACTIVITY, timestamps, version. Indexes: (assignee_id,status), (department_id,status), (due_date,status), project_id.
- **task_tags**: (task_id, tag_id) PK
- **task_comments**: task_id, author_id, body TEXT, timestamps, edited
- **task_attachments**: (task_id, file_id) PK, added_by, added_at
- **task_checklists**: task_id, content, is_done, done_by, done_at, position
- **task_dependencies**: task_id, depends_on_task_id, UQ(pair), CHECK(task_id <> depends_on_task_id)
- **task_watchers**: (task_id, user_id) PK
- **task_history**: task_id, changed_by, field_name, old_value, new_value, changed_at. Covers reassignment, status changes and reopen.

### Help desk & SLA
- **sla_policies**: name, priority (UQ among active policies), first_response_minutes, resolution_minutes, warning_threshold_pct (default 75), active. Seeded with URGENT 60/240, HIGH 120/480, MEDIUM 240/1440, LOW 480/2880.
- **ticket_categories**: name (UQ), description, default_department_id, active
- **tickets**: code (UQ), subject, description, requester_id, department_id, category_id, priority, assignee_id, status NEW/OPEN/IN_PROGRESS/WAITING_FOR_REQUESTER/RESOLVED/CLOSED, sla_policy_id, first_response_due_at, resolution_due_at (snapshotted at creation), first_responded_at, resolved_at, closed_at, sla_paused_at, sla_paused_minutes, timestamps, version. Indexes: (status,priority), (assignee_id,status), resolution_due_at, FULLTEXT(subject,description).
- **ticket_comments**: ticket_id, author_id, body, is_internal, created_at. The first agent comment sets `first_responded_at`.
- **ticket_attachments**: (ticket_id, file_id) PK
- **ticket_history**: same shape as task_history

### Approvals
- **approval_types**: code (UQ), name, description, requires_amount, active. Seeded with ACCESS, SOFTWARE, PURCHASE, EXPENSE, MARKETING_CREATIVE, RECRUITMENT, OTHER.
- **approval_type_steps**: approval_type_id, step_order, approver_kind (DEPARTMENT_MANAGER/ROLE/USER), approver_role_id, approver_user_id, UQ(type, step_order). This is the configurable workflow.
- **approvals**: code (UQ), approval_type_id, title, description, requester_id, department_id, amount, currency, due_date, status PENDING/APPROVED/REJECTED/CANCELLED, current_step, decided_at, timestamps, version
- **approval_steps**: approval_id, step_order, approver_id, status PENDING/APPROVED/REJECTED/SKIPPED, comment, decided_at, UQ(approval_id, step_order)
- **approval_attachments**: (approval_id, file_id) PK

### Collaboration
- **announcements**: title, body, target_department_id NULL (NULL means everyone), priority, publish_at, expires_at, ack_required, created_by, timestamps
- **announcement_reads**: (announcement_id, user_id) PK, read_at, acknowledged_at
- **knowledge_categories**: name (UQ), slug, description, position. Seeded with SOP, HR, IT, Security, Development, Marketing, Operations, FAQ, Troubleshooting.
- **knowledge_articles**: category_id, title, slug (UQ), body MEDIUMTEXT (markdown), status DRAFT/PUBLISHED/ARCHIVED, author_id, department_id NULL, published_at, view_count, timestamps, FULLTEXT(title, body)
- **article_tags**: (article_id, tag_id) PK
- **article_attachments**: (article_id, file_id) PK
- **documents**: name, description, department_id, project_id NULL, uploaded_by, current_version_id NULL, timestamps
- **document_versions**: document_id, version_no, file_id, change_note, uploaded_by, created_at, UQ(document_id, version_no)

### Cross-cutting
- **notifications**: user_id, type, title, body, entity_type, entity_id, read_at, created_at, INDEX(user_id, read_at)
- **calendar_events**: title, description, event_type TEAM_EVENT/LEAVE/IMPORTANT_DATE/MEETING, start_at, end_at, all_day, department_id NULL, user_id NULL (for leave), created_by. Task deadlines, milestones and approval due dates are **merged in at query time**, not copied here.
- **audit_logs**: actor_id NULL, action (LOGIN, TASK_CREATED, TARGET_UPDATED, …), entity_type, entity_id, details JSON (before/after), ip_address, user_agent, created_at. Indexes: (entity_type, entity_id) and (created_at).

### Digital Marketing
- **marketing_pages**: url (UQ), title, page_type SERVICE/INDUSTRY/LOCATION/BLOG/LANDING_PAGE/PRODUCT/OTHER, primary_keyword, department_id, owner_id, status ACTIVE/INACTIVE/ARCHIVED, timestamps
- **marketing_keywords**: page_id, keyword, search_engine DEFAULT 'GOOGLE', location, device DESKTOP/MOBILE, target_position, current_position NULL, previous_position NULL, search_volume, keyword_difficulty, owner_id, status, last_ranked_at, timestamps. UQ(page_id, keyword, search_engine, location, device). NULL position means **Not Ranked**. `current_position`/`previous_position` are a cache refreshed from the latest history row, and history stays the source of truth.
- **keyword_ranking_history**: keyword_id, page_id (snapshot), ranking_month TINYINT CHECK 1–12, ranking_year SMALLINT, ranking_position SMALLINT NULL (NULL = NR, CHECK 1–100 when set), previous_position, ranking_change, search_volume, notes, source MANUAL/CSV/SEMRUSH/GSC, recorded_by, created_at, updated_at. **UQ(keyword_id, ranking_month, ranking_year)**. Each new month inserts a new row and older rows are never touched. A correction to the same month updates that row and writes an audit log entry.
- **marketing_target_types**: code (UQ), name, unit COUNT/CURRENCY/PERCENT, actual_source (MANUAL / LEADS / LEADS_BY_SOURCE / BACKLINKS_LIVE / BLOGS_PUBLISHED / KEYWORDS_TOP10 / EMAIL_CAMPAIGNS / PAID_CAMPAIGNS / LANDING_PAGES), lead_source_filter NULL, behind_threshold_pct NULL (overrides the global setting), active, position. Seeded with the 13 types from the brief. `actual_source` is how **leads connect to targets**: actuals for auto types are aggregated from the underlying records.
- **marketing_targets**: target_type_id, month, year, target_value DECIMAL(14,2), actual_value DECIMAL NULL (used only for MANUAL types), owner_id, department_id, notes, timestamps, version. **UQ(target_type_id, month, year)**.
- **marketing_activities**: name, description, department_id, owner_id, frequency DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY, start_date, end_date NULL, due_offset_days, task_title_template (for example `Update {month} keyword rankings`), default_assignee_id, active, created_at, updated_at
- **marketing_activity_checklist_items**: activity_id, content, position. This is the template, copied into the generated task's checklist.
- **marketing_activity_occurrences**: activity_id, period_start, period_end, due_date, status PENDING/IN_PROGRESS/COMPLETED/SKIPPED/OVERDUE, task_id NULL → tasks, completed_at, completed_by, notes. **UQ(activity_id, period_start)**. When the linked task completes, the occurrence completes and the next one is created along with its task, in one transaction and idempotent through the UQ. A daily scheduler backfills missing current-period occurrences.
- **email_campaigns**: name, campaign_type NEWSLETTER/LEAD_GENERATION/PRODUCT_PROMOTION/EVENT/RECRUITMENT/OTHER, campaign_date, owner_id, audience, emails_sent, delivered, bounced, opened, unique_opens, clicked, unique_clicks, unsubscribed, leads_generated, status DRAFT/SCHEDULED/SENT/CANCELLED, notes, provider MANUAL/ZOHO, external_id, UQ(provider, external_id), timestamps
- **paid_campaigns**: name, platform (VARCHAR, LINKEDIN for now), objective, start_date, end_date, budget, currency, owner_id, status DRAFT/ACTIVE/PAUSED/COMPLETED, notes, provider, external_id, timestamps
- **paid_campaign_metrics**: campaign_id, month, year, amount_spent, impressions, clicks, leads, conversions, UQ(campaign_id, month, year). Monthly rows make month filters correct for campaigns that span months. Campaign totals are SUMs, and CTR/CPL/conversion rate are computed.
- **content_items**: title, url, content_type BLOG/CASE_STUDY/WHITEPAPER/LANDING_PAGE/OTHER, author_id, owner_id, publication_date, target_keyword_id NULL, target_keyword_text, target_page_id NULL, status IDEA/PLANNED/IN_PROGRESS/DRAFT/PUBLISHED/UPDATED, organic_traffic, cta_clicks, timestamps
- **content_item_attachments**: (content_item_id, file_id) PK
- **backlinks**: target_page_id NULL, target_url, referring_domain, link_url, anchor_text, link_type, status PROSPECTED/SUBMITTED/APPROVED/LIVE/REJECTED/LOST, submitted_date, approved_date, live_date, owner_id, domain_authority, notes, timestamps. Indexes on each date column for the monthly counts.
- **marketing_leads**: code (UQ), name, company, email, phone, source ORGANIC/EMAIL/LINKEDIN/PAID_CAMPAIGN/BLOG/WEBSITE/REFERRAL/OTHER, email_campaign_id NULL, paid_campaign_id NULL, content_item_id NULL, department_id, lead_date, status NEW/CONTACTED/QUALIFIED/CONVERTED/LOST, owner_id, notes, timestamps, INDEX(lead_date, source)
- **marketing_monthly_reports**: month, year, payload JSON (a frozen snapshot), generated_by, generated_at, UQ(month, year)

### Permissions catalogue (seeded)
- Core: `DASHBOARD_VIEW`, `TASK_VIEW/CREATE/EDIT/ASSIGN/DELETE`, `WORKLOAD_VIEW`, `PROJECT_VIEW/EDIT`, `TICKET_VIEW/CREATE/EDIT/ASSIGN`, `SLA_MANAGE`, `APPROVAL_VIEW/DECIDE/CONFIGURE`, `ANNOUNCEMENT_MANAGE`, `KB_VIEW/EDIT`, `DOCUMENT_VIEW/EDIT`, `TEAM_VIEW`, `CALENDAR_VIEW/EDIT`, `REPORT_VIEW/EXPORT`, `USER_MANAGE`, `DEPARTMENT_MANAGE`, `SETTINGS_MANAGE`, `PERMISSION_MANAGE`, `AUDIT_VIEW`
- Marketing: `MARKETING_VIEW/EDIT`, `SEO_VIEW/EDIT`, `CAMPAIGN_VIEW/EDIT`, `TARGET_VIEW/EDIT`, `LEAD_VIEW/EDIT`, `BACKLINK_VIEW/EDIT`, `CONTENT_VIEW/EDIT`
- SUPER_ADMIN gets all permissions. DEPARTMENT_MANAGER gets core view/edit, scoped to their department. EMPLOYEE gets view plus edit on their own work. Marketing permissions go to Digital Marketing users through `user_permissions`.

### Flyway migration order
Each migration ships **in the phase that first needs it**, which keeps `ddl-auto=validate` green after every phase.

| # | File | Contents | Phase |
|---|---|---|---|
| V1 | `V1__identity_and_org.sql` | departments, users, roles, permissions, role_permissions, user_roles, user_permissions, department_members, refresh_tokens, app_settings, code_sequences, files, tags, audit_logs | 3 |
| V2 | `V2__reference_data.sql` | roles, full permission catalogue, role_permissions, 10 departments, app_settings defaults, code_sequences | 3 |
| V3 | `V3__projects.sql` | projects, members, milestones, risks, dependencies, attachments | 5 |
| V4 | `V4__tasks.sql` | tasks + tags/comments/attachments/checklists/dependencies/watchers/history | 5 |
| V5 | `V5__notifications_calendar.sql` | notifications, calendar_events | 6 |
| V6 | `V6__tickets_sla.sql` | sla_policies (seeded), ticket_categories (seeded), tickets, comments, attachments, history | 7 |
| V7 | `V7__approvals.sql` | approval_types (seeded), approval_type_steps, approvals, approval_steps, approval_attachments | 8 |
| V8 | `V8__collaboration.sql` | announcements, reads, knowledge categories (seeded)/articles/tags/attachments, documents, versions | 8 |
| V9 | `V9__marketing_seo.sql` | marketing_pages, marketing_keywords, keyword_ranking_history | 10 |
| V10 | `V10__marketing_targets.sql` | marketing_target_types (13 seeded), marketing_targets | 12 |
| V11 | `V11__marketing_activities.sql` | activities, checklist items, occurrences | 13 |
| V12 | `V12__marketing_campaigns.sql` | email_campaigns, paid_campaigns, paid_campaign_metrics | 14–15 |
| V13 | `V13__marketing_content_backlinks.sql` | content_items, content_item_attachments, backlinks | 17–18 |
| V14 | `V14__marketing_leads.sql` | marketing_leads (FKs to campaigns + content) | 16* |
| V15 | `V15__marketing_reports.sql` | marketing_monthly_reports | 20 |

\*Leads depend on content_items, so V13's content/backlink tables are created before V14 even though their UI ships in Phases 17–18. The plan pulls V13 forward into Phase 16. Migrations are never edited after they are applied; every change gets a new `V{n}`.

**Dev seed data stays out of Flyway**. It lives in a Java `DevDataSeeder` (`@Profile("dev")`, idempotent, skips if already seeded) so that "due today" and "overdue" dates are **relative to the current date** and passwords are BCrypt-hashed at runtime. It seeds Rakesh (`rakesh@teamops.local`, SUPER_ADMIN), about 2 users per department, tasks, tickets, projects, and the sample marketing data from brief §72–77 (SAP pages and keywords with Aug/Sep/Oct history, Oct-2026 targets 250/200, backlinks 50/35/28/22, blogs 12/9, the LinkedIn ₹50k/₹42k/84 campaign, the Zoho 25k/24k/8.5k/1.25k/185 campaign). The dev password comes from `DEV_SEED_PASSWORD` in `.env` and is documented in the README. In prod, the first Super Admin is created from `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` only when there are no users.

### `.env` keys (`backend/.env.example` holds placeholders, `backend/.env` holds the real dev values)
`DB_URL=jdbc:mysql://localhost:3306/team_ops_board?serverTimezone=UTC`, `DB_USERNAME=teamops`, `DB_PASSWORD=…`, `JWT_SECRET=` (at least 256-bit random), `JWT_ACCESS_TTL_MINUTES=60`, `JWT_REFRESH_TTL_DAYS=7`, `CORS_ALLOWED_ORIGINS=http://localhost:5173`, `FILE_STORAGE_DIR=./uploads`, `FILE_MAX_SIZE_MB=20`, `DEV_SEED_PASSWORD=…`, `SPRING_PROFILES_ACTIVE=dev`. Frontend: `VITE_API_BASE_URL=http://localhost:8080/api`.

---

## Phase 2d: Key business-rule decisions
- **Workload %** = remaining estimated hours of active tasks (TODO/IN_PROGRESS/BLOCKED/IN_REVIEW) due within the workload window (default 14 days, overdue included) ÷ (weekly_capacity_hours × window weeks) × 100. Remaining hours = max(estimated − actual, 0). An unestimated task counts as `default_task_hours` (default 4). Levels: ≤40 LOW, ≤70 NORMAL, ≤100 HIGH, >100 OVERLOADED.
- **Target status**: actual ≥ target → ACHIEVED (green). Achievement % < threshold (default 60%, set globally or per type) → BEHIND (red). Otherwise IN_PROGRESS (orange). Remaining = max(target − actual, 0).
- **SEO**: positions 1–10 are TOP_10 (green), 11–100 are RANKING (orange), NULL is NOT_RANKED (red). Change = previous − current, so a positive number means improved. Every status carries a label and icon, never colour alone.
- **SLA**: due timestamps are snapshotted at ticket creation. State is ON_TRACK, WARNING (≥ warning % elapsed) or BREACHED. The clock pauses while a ticket is WAITING_FOR_REQUESTER.
- All rate formulas follow brief §80. Division by zero returns `null`, which the UI shows as "—".

---

## Implementation plan (each phase ends with `mvnw verify` on JDK 21 + `npm run build` + tests, and keeps the app runnable)

| Phase | Deliverable |
|---|---|
| **3 Auth** | `git init` + .gitignore. Scaffold backend (Boot 4.x, wrapper, enforcer, .env import) and frontend (Vite+TS+Tailwind+shadcn). V1+V2. Login/refresh/logout/me endpoints, JWT filter chain, CORS, GlobalExceptionHandler, audit of LOGIN. Frontend `/login`, AuthProvider, ProtectedRoute, `/unauthorized`, AppShell with the role-aware sidebar. Bootstrap admin + skeleton DevDataSeeder. Tests: auth service, security integration (401/403). |
| **4 Users/Departments/Roles** | CRUD for users (create/disable/role and permission grants) and departments (manager, members). AccessScopeService. Admin pages: Users, Departments. Team directory + profile shell. Seeder adds users for all 10 departments. |
| **5 Tasks + Workload** | V3+V4. Task CRUD, assign/reassign, status/priority, comments, checklist, watchers, dependencies, tags, attachments (FileStorageService), history, reopen. `/api/workload` aggregate query + filters/sorts. Pages: Tasks (table + drawer), My Tasks, Workload. Projects table exists but has no UI yet. Tests: workload calculation, task authorization. |
| **6 Dashboard** | V5. Admin/manager/employee dashboard endpoints (a few aggregate queries each). KPI row, status donut, department bar chart, employee workload table, weekly completion chart, overdue and upcoming lists, recent activity. Notifications (bell + in-app list). Calendar API that merges task deadlines and events. |
| **7 Tickets + SLA** | V6. Ticket CRUD with TKT codes, search/filter/sort/pagination, comments, SLA snapshot/state/compliance, SLA policy admin. Pages: Tickets, My Tickets, SLA. Tests: SLA calculation. |
| **8 Projects + Approvals (+ Collaboration)** | V7+V8. Projects UI (milestones, risks, progress). Approval workflow engine plus pages. Announcements (read/ack), Knowledge Base (FULLTEXT search), Documents (versions), Calendar UI (month/week/agenda). |
| **9 DM foundation** | Marketing permissions in the UI, the `/digital-marketing` route group guarded by backend `MARKETING_VIEW`, MarketingFilterBar (month/year/owner), shared marketing components, provider interfaces, CSV import framework (validate → preview valid/invalid/errors → commit). |
| **10 SEO pages + keywords** | V9. Page and keyword CRUD, page detail with KPI stats. RankingBadge. |
| **11 Monthly rankings** | Record a monthly ranking (insert-only per month), refresh the keyword cache, history line chart, SEO table filters/sorts, monthly bucket report with previous-month comparison, CSV import/export. Tests: ranking status, history uniqueness. |
| **12 Targets** | V10. Target types admin, monthly targets, auto-actual aggregation, TargetProgress cards, table, month/quarter/year trend. Tests: 250/200 gives 80% with 50 remaining; 250/275 gives 110% with 0 remaining. |
| **13 Recurring activities** | V11. Activities CRUD, occurrence generation tied to task completion, daily scheduler, due notifications. Tests: next-occurrence generation for every frequency. |
| **14 Email campaigns** | V12 (email part). CRUD, metrics, monthly summary/comparison, CSV. Tests: open rate 8,500/24,000 = 35.42%. |
| **15 LinkedIn paid campaigns** | V12 (paid part). Campaign + monthly metrics, budget progress, spend-vs-leads chart. Tests: CPL ₹42,000/84 = ₹500. |
| **16 Leads** | V13 tables + V14. Lead CRUD, source attribution, lead-source chart against the target, CSV. |
| **17 Backlinks** / **18 Content & Blog** | UIs and monthly metrics for backlinks and content. Tests: backlink and content calculations. |
| **19 DM dashboard** | `/api/marketing/dashboard` built from a handful of aggregate queries. Executive dashboard plus the Rakesh summary on the home dashboard. |
| **20 Reports** | Core reports + `/digital-marketing/reports` with month-over-month deltas, CSV export, frozen monthly snapshot (V15), ReportExporter seam for PDF. |
| **21 Admin + Audit** | Settings UI (thresholds, capacity defaults), permission matrix, audit log viewer with filters, global search. |
| **22–24** | UI polish (empty/error/skeleton states, dark mode, responsive), test hardening, performance work (indexes and EXPLAIN on dashboard queries, query caching, bundle splitting). Docs: README, architecture.md, database.md, api.md, digital-marketing.md. |

## Critical files to be created first (Phase 3)
`backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/.env.example`, `backend/src/main/resources/db/migration/V1__identity_and_org.sql`, `V2__reference_data.sql`, `backend/src/main/java/com/teamops/common/security/*`, `frontend/package.json`, `frontend/src/routes/*`, `frontend/src/layouts/*`, `.gitignore`, `README.md`.

## Verification (per phase, starting Phase 3)
1. `set JAVA_HOME=…\jdk-21.0.12.101-hotspot` and run `backend\mvnw.cmd verify`. Unit and integration tests must pass, and Flyway must migrate `team_ops_board` cleanly (this also confirms the `.env` credentials).
2. `backend\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`, then hit `/actuator/health` and log in as Rakesh through `POST /api/auth/login`.
3. `cd frontend && npm run build && npm run test`, then `npm run dev` and log in through the browser to check the role-based sidebar and protected routes.
4. Authorization check: an EMPLOYEE token calling a SUPER_ADMIN or marketing endpoint gets 403.
