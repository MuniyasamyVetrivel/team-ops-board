# Database

One MySQL 8 schema, `team_ops_board`, owned by Flyway. Hibernate only validates it (`ddl-auto=validate`), so every
change is a new migration. See [architecture.md](architecture.md) for how the application reads it.

## Conventions

| Rule | Detail |
|---|---|
| Engine and charset | InnoDB, `utf8mb4` (`utf8mb4_0900_ai_ci`) |
| Keys | Signed `BIGINT AUTO_INCREMENT` ids, mapped to `Long`. Foreign keys are signed too, so there are no signedness mismatches |
| Time | `DATETIME(6)` in UTC, mapped to `Instant`. The JDBC URL forces the session time zone to UTC. Business dates (due dates, months) are `DATE`/`INT` and are interpreted in `APP_TIME_ZONE` |
| Money | `DECIMAL(14,2)` plus a `VARCHAR(3)` currency column, default `INR` |
| Text | `VARCHAR(n)` (never `CHAR`); long text is `TEXT`/`MEDIUMTEXT` (knowledge base articles); audit details and frozen reports are `JSON` |
| Enums | Stored as `VARCHAR`, mapped with `@Enumerated(STRING)` + `@JdbcTypeCode(VARCHAR)` |
| Audit stamps | Every record table has `created_at`, `created_by`, `updated_at`, `updated_by`, mapped by `AuditedEntity` (`BaseEntity` adds the id) and filled by Hibernate: the signed-in user, or NULL for the system. The `*_by` columns have no foreign keys so they outlive removed users. Pure join tables are not records; their changes are logged on the owning record |
| Soft delete | Recycle-bin tables keep deleted rows with `deleted_at`/`deleted_by`; entities extend `SoftDeletableEntity` and carry `@SQLRestriction("deleted_at IS NULL")`, so Hibernate never returns them. Hand-written SQL over these tables must add `deleted_at is null` itself (`TaskWorkScope.clause` does it for tasks). Unique keys still cover deleted rows, so a unique value is released only by the permanent delete |
| Optimistic locking | Editable records have a `version` column (`@Version`); stale writes answer 409 `STALE_UPDATE` |
| Derived values | Never stored: rates, remaining, achievement %, target status, workload %, SLA state and project progress are computed in services. The exceptions are deliberate snapshots, such as SLA due times taken at ticket creation and the previous position recorded with each monthly ranking |
| Codes | `TSK-`, `TKT-`, `PRJ-`, `APR-`, `LEAD-`, `BLK-` codes come from `code_sequences` (one row per prefix, read with `SELECT … FOR UPDATE` inside the caller's transaction) |
| Dev data | Never in Flyway. Demo data lives only in the test sources (`DevDataSeeder`); real users come from the bootstrap admin and the users CSV import ([go-live-data.md](go-live-data.md)) |

## Migrations

| Version | Adds |
|---|---|
| V1 identity_and_org | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `user_permissions`, `departments`, `department_members`, `refresh_tokens`, `app_settings`, `code_sequences`, `files`, `tags`, `audit_logs` |
| V2 reference_data | The 10 departments, the three roles, the permission catalogue and role grants, default settings and code sequences |
| V3 projects | `projects`, `project_members`, `project_milestones`, `project_risks`, `project_dependencies`, `project_attachments` |
| V4 tasks | `tasks`, `task_tags`, `task_comments`, `task_attachments`, `task_checklists`, `task_dependencies`, `task_watchers`, `task_history` |
| V5 notifications_calendar | `notifications`, `calendar_events` |
| V6 tickets_sla | `sla_policies` (brief defaults), `ticket_categories`, `tickets`, `ticket_comments`, `ticket_attachments`, `ticket_history` |
| V7 approvals | `approval_types` (seven request types with their workflows), `approval_type_steps`, `approvals`, `approval_steps` |
| V8 collaboration | `announcements`, `announcement_reads`, `knowledge_categories` (the brief's categories), `knowledge_articles` (FULLTEXT), `article_tags`, `article_attachments`, `documents`, `document_versions` |
| V9 marketing_seo | `marketing_pages`, `marketing_keywords`, `keyword_ranking_history` |
| V10 marketing_targets | `marketing_target_types` (the 13 brief types), `marketing_targets` |
| V11 marketing_activities | `marketing_activities`, `marketing_activity_checklist_items`, `marketing_activity_occurrences` |
| V12 marketing_email_campaigns | `email_campaigns` |
| V13 marketing_paid_campaigns | `paid_campaigns`, `paid_campaign_metrics` (monthly results) |
| V14 marketing_content_items | `content_items` (blog posts and other content) |
| V15 marketing_leads | `marketing_leads` |
| V16 marketing_backlinks | `backlinks` |
| V17 marketing_content | Extends `content_items` for content tracking (Phase 18) |
| V18 marketing_content_attachments | `content_item_attachments` |
| V19 marketing_monthly_reports | `marketing_monthly_reports` (frozen monthly reports, JSON payload) |
| V20 admin_settings_roles | `version` on `app_settings`, `approval_types` and `roles`; default weekly capacity setting; audit indexes |
| V21 performance_indexes | Indexes for task KPIs and reports, and for the newest-first lead, backlink and content lists (Phase 24) |
| V22 remove_leave_and_hr_policy_content | Leave moved to Zoho People: deletes `LEAVE` calendar events, drops `calendar_events.user_id`, removes the leave-policy announcement, article, document, ticket and task from earlier dev seeds |
| V23 celebrations_important_days_events | `CELEBRATIONS_EDIT`, the reminder days-before setting, `employees`, `important_days`, `marketing_events`, `linkedin_posts`, `occasion_attachments` |
| V24 work_history_indexes | Team member work history: `tasks (assignee_id, status, completed_at)` and `(assignee_id, due_date)`, `tickets (assignee_id, status, resolved_at)` and `(assignee_id, resolution_due_at)`, replacing the `(assignee_id, status)` indexes |
| V25 user_password_change_and_time_zone | `users.must_change_password` (an admin-set password must be replaced at the next sign-in) and `users.time_zone` |
| V26 audit_history | Phase X1: `created_at`/`created_by`/`updated_at`/`updated_by` on every record table (backfilled from the author, requester, uploader or recorder column where there was one, otherwise NULL = system); `audit_logs.entity_label`, `parent_type`, `parent_id`, `finance` and `(entity_type, entity_id, created_at)` / `(parent_type, parent_id, created_at)` indexes; the `FINANCE_VIEW` permission (Super Admin) |
| V27 recycle_bin | Phase X2: `deleted_at`, `deleted_by` and a `deleted_at` index on the 17 recycle-bin tables (`tasks`, `announcements`, `calendar_events`, `employees`, `important_days`, `marketing_events`, `documents`, `marketing_activities`, `backlinks`, `content_items`, `email_campaigns`, `marketing_leads`, `paid_campaigns`, `marketing_keywords`, `marketing_pages`, `marketing_targets`, `marketing_target_types`); `recycle_bin_items`; the `recycleBin.retentionDays` setting (90) |
| V28 super_admin_only_tools | Expansion access rule: `HISTORY_VIEW` and `RECYCLE_BIN_MANAGE` on `SUPER_ADMIN` only (no other role holds an expansion permission) |
| V29 recycle_bin_purge_permission | Phase X2: `RECYCLE_BIN_PURGE` (permanent delete), split from `RECYCLE_BIN_MANAGE` (view and restore); Super Admin only |
| V30 backups | Phase X3: `backup_runs` (backup history: trigger, status, start and end, file, size, SHA-256 checksum, target, error, who asked, when retention removed the file); the `BACKUP_VIEW` and `DATA_EXPORT` permissions (Super Admin) |
| V31 backup_second_copy | Phase X3: `backup_runs.secondary_target`, `secondary_status`, `secondary_error`, `secondary_removed_at` (the optional second copy, `BACKUP_SECONDARY_TARGET`) and a `(secondary_status, started_at)` index |
| V32 employee_id_and_profile_photo | Phase X4: `users.employee_id` (unique; backfilled from the Employee record linked to the user, NULL for anyone else until a Super Admin sets it) and `users.photo_file_id` (the profile photo, a square JPEG in `files`) |
| V33 department_modules | Phase X5: `department_modules` (the switch of a department tool for a department: `module_key`, `department_id`, `enabled`; unique `(module_key, department_id)`, deleted with the department); the `MODULE_MANAGE` permission (Super Admin) |
| V34 bench_consultants_clients | Phase X6, part 1: `bench_consultants` (bench details of an Employee record), `bench_consultant_skills`, `bench_consultant_certifications`, `bench_clients`, `bench_client_engagements`; `BENCH_VIEW`, `BENCH_EDIT` (Super Admin); the `bench` tool switched on for `HR` |
| V35 bench_allocations | Phase X6, part 2: `bench_allocations` (consultant, engagement, role, dates, 1–100 %, billable) |
| V36 vendors | Phase X7: `vendors`, `vendor_contacts`, `vendor_contracts`, `vendor_contract_documents`; `VENDOR_VIEW`, `VENDOR_EDIT` (Super Admin); the `vendors` tool switched on for `HR` |
| V37 finance_licences_rates | Phase X8, part 1: `finance_licences`, `finance_licence_assignments` (seats used), `finance_exchange_rates` (to AUD); `FINANCE_EDIT` (Super Admin; `FINANCE_VIEW` came with V26); the `finance` tool switched on for `HR` |
| V38 finance_expenses_budgets | Phase X8, part 2: `finance_expenses` (with the stored AUD rate, rate date and AUD amount, receipt, optional approval), `finance_budgets` (AUD, one per department and month); the `finance.defaultCurrency` setting (INR) |
| V39 receipt_audit_owner | Phase X8: files the audit entries of expense receipts under their expense (`parent_type`/`parent_id`), so they are masked like the expense |
| V40 bench_for_talent_acquisition | Phase X6 fix: switches the `bench` tool on for Talent Acquisition (code `TA`; V34 looked for `TALENT_ACQUISITION`, which does not exist), unless that department already has a bench row |

Never edit an applied migration; add `V{n+1}__description.sql` instead, in the phase that first needs it.

## Tables by module

### Identity and organisation

| Table | Purpose | Notable constraints |
|---|---|---|
| `users` | People: employee ID, names, job title, contact, department, `reports_to_id`, weekly capacity, status, profile photo, BCrypt hash | Unique `email`, unique `employee_id` |
| `roles`, `permissions` | Role and permission catalogue | Unique `code` |
| `user_roles`, `role_permissions`, `user_permissions` | Role membership, role grants and direct (per-person) grants | Composite primary keys |
| `departments` | Department with optional `manager_id` and status | Unique `code`, unique `name` |
| `department_members` | Secondary memberships (`MEMBER` or `MANAGER`) | Composite primary key |
| `refresh_tokens` | SHA-256 hash of each refresh token, expiry, revocation time, user agent and IP | Unique `token_hash` |
| `audit_logs` | Actor, action, entity (type, id, label), parent record, JSON details, finance flag, IP and user agent. Append-only | Indexes on `(action, created_at)`, `(actor_id, created_at)`, `(entity_type, entity_id, created_at)`, `(parent_type, parent_id, created_at)` |
| `app_settings` | Admin settings (workload window, default task hours, SLA warning %, marketing behind %, upload limit, default capacity, celebration reminder days, recycle bin retention, finance default currency) | Unique `setting_key` |
| `recycle_bin_items` | One row per deleted record: type, id, label, who deleted it and when; the record itself keeps its row with `deleted_at` | — |
| `code_sequences` | Next number per code prefix | One row per prefix |

### Work management

| Table | Purpose | Notable constraints |
|---|---|---|
| `tasks` | Task with department, project/milestone, assignee, creator, priority, status, dates, estimate/actual hours, source | Unique `code`; indexes on `(assignee_id, status, completed_at)`, `(assignee_id, due_date)`, `(department_id, status)`, `(due_date, status)`, `(status, completed_at)`, `created_at`, `completed_at` |
| `task_comments`, `task_checklists`, `task_watchers`, `task_dependencies`, `task_tags` | Task detail collections | — |
| `task_history` | Field-level change history shown on the task | — |
| `files`, `task_attachments` | Stored file metadata and its links to tasks (also used by tickets, projects, KB and content) | Unique `storage_key` |
| `projects`, `project_members`, `project_milestones`, `project_risks`, `project_dependencies`, `project_attachments` | Projects and their parts | Unique project `code` |
| `notifications` | In-app notifications | Unique `(user_id, dedup_key)` so reminders are sent once |
| `calendar_events` | Company and department events, meetings and important dates | — |

### Celebrations, important days and events

Birthdays and work anniversaries are computed from the employee's dates every year (29 February falls on 28 February
otherwise); occurrences are never stored.

| Table | Purpose | Notable constraints |
|---|---|---|
| `employees` | Staff and consultants (most never sign in): employee ID, names, date of birth, designation, reporting manager, joining date, department, work email, office, photo, optional app user, `ACTIVE`/`LEFT` | Unique `employee_code`, unique `user_id` |
| `important_days` | Festivals, national and industry days, company milestones per region; `YEARLY` or `ONE_OFF` | Unique `(name, day_date, region)` |
| `marketing_events` | Events, webinars, event and email marketing with owner, links, status and optional email campaign | Index on `start_at` |
| `linkedin_posts` | Post tracking per occurrence (`subject_type`, `subject_id`, `occurrence_date`) | **Unique `(subject_type, subject_id, occurrence_date)`** |
| `occasion_attachments` | Files on important days and events | Unique `file_id` |

### Help desk

| Table | Purpose | Notable constraints |
|---|---|---|
| `tickets` | Ticket with requester, department, category, priority, assignee, status and the SLA due times snapshotted at creation, plus paused minutes | Unique `code` (`TKT-000001`); indexes for queues and `resolution_due_at` |
| `ticket_categories` | Categories and the department that handles each | Unique `name` |
| `ticket_comments`, `ticket_history`, `ticket_attachments` | Public replies and internal notes, history, files | — |
| `sla_policies` | First response and resolution minutes per priority | Unique `priority` |

### Collaboration

| Table | Purpose | Notable constraints |
|---|---|---|
| `approval_types`, `approval_type_steps` | Configurable workflows | Unique type `code`; unique `(approval_type_id, step_order)` |
| `approvals`, `approval_steps` | Requests with their own copy of the steps | Unique `code`; unique `(approval_id, step_order)` |
| `announcements`, `announcement_reads` | Announcements and per-person read and acknowledgement receipts | — |
| `knowledge_categories`, `knowledge_articles`, `article_tags`, `article_attachments` | Knowledge base (FULLTEXT search) | Unique slug and name |
| `documents`, `document_versions` | Documents with numbered versions | Unique `(document_id, version_no)` |
| `department_modules` | Which department tools (modules defined in code) are switched on for which department (Phase X5, docs/adding-a-module.md); switching off keeps the row with `enabled` false | Unique `(module_key, department_id)` |
| `backup_runs` | Backup history (Phase X3, docs/backup-and-restore.md); a log, so not in record history | — |

### Department tools (Phases X6–X8)

Each tool's tables belong to a module (`department_modules` switch, own permissions). Statuses, bench days,
utilisation, renewal dates, seats used and budget figures are computed, never stored. The recycle-bin tables among
them carry `deleted_at`/`deleted_by`.

| Table | Purpose | Notable constraints |
|---|---|---|
| `bench_consultants` | Bench details of an Employee record: experience, location, availability date, HR employment status (`employee_id` without a foreign key, so a consultant outlives its employee in the bin) | Unique `employee_id`; recycle bin |
| `bench_consultant_skills`, `bench_consultant_certifications` | A consultant's skills and certifications | Unique `(consultant_id, name)` for skills |
| `bench_clients`, `bench_client_engagements` | Clients and their engagements (separate from internal projects) | Unique client `name`; unique `(client_id, name)`; recycle bin |
| `bench_allocations` | Consultant on an engagement: role, dates, allocation %, billable | Recycle bin |
| `vendors`, `vendor_contacts` | Vendors (unique name, category, status, rating) and their contacts | Unique `name`; recycle bin (vendors) |
| `vendor_contracts`, `vendor_contract_documents` | Contracts (dates, value and currency, renewal, auto-renew, notice) and their files | Unique `(vendor_id, title)`; unique `file_id`; recycle bin (contracts) |
| `finance_licences`, `finance_licence_assignments` | Tools and licences, and the users assigned to them (seats used) | Unique `name`; unique `(licence_id, user_id)`; recycle bin (licences) |
| `finance_exchange_rates` | Rate to AUD per currency from a date | Unique `(currency, effective_from)`; recycle bin |
| `finance_expenses` | Expenses with GST, the AUD rate, rate date and AUD amount taken at the time, receipt, optional approval | Unique `receipt_file_id`; recycle bin |
| `finance_budgets` | AUD budget per department and month | Unique `(department_id, budget_year, budget_month)`; recycle bin |

### Digital Marketing

See [digital-marketing.md](digital-marketing.md) for the rules these tables support.

| Table | Purpose | Notable constraints |
|---|---|---|
| `marketing_pages` | Website pages (URL, title, type, primary keyword, owner, status) | Unique `url` |
| `marketing_keywords` | Keywords per page with engine, location, device, target and cached current/previous position | Unique `(page_id, keyword, search_engine, location, device)` |
| `keyword_ranking_history` | One row per keyword and month: position (NULL = not ranked), previous position and change snapshotted at recording, search volume, notes | **Unique `(keyword_id, ranking_month, ranking_year)`**: insert-only per month |
| `marketing_target_types` | Configurable target types and where their actual comes from (`actual_source`, optional lead source filter, behind-threshold override) | Unique `code`, unique `name` |
| `marketing_targets` | Monthly target values (and the actual for manual types) | **Unique `(target_type_id, month, year)`** |
| `marketing_activities`, `marketing_activity_checklist_items` | Recurring activities: frequency, dates, due offset, task template, default assignee | — |
| `marketing_activity_occurrences` | One row per activity and period, with its status, completion and generated task | **Unique `(activity_id, period_start)`**, unique `task_id` |
| `email_campaigns` | Zoho-style campaign counts (sent, delivered, bounced, opens, clicks, unsubscribes, leads) | Unique `(provider, external_id)` |
| `paid_campaigns`, `paid_campaign_metrics` | LinkedIn (and later other) campaigns, with budget and monthly results (spend, impressions, clicks, leads, conversions) | Unique `(campaign_id, month, year)` for results |
| `marketing_leads` | Leads with source, status, owner and optional link to an email campaign, paid campaign or content item | Unique `code`; indexes on `(lead_date, source)`, `lead_date`, `updated_at`, `(status, lead_date)` |
| `backlinks` | Backlinks with stage dates (submitted, approved, live, rejected, lost) and domain authority | Unique `code`; one index per stage date, plus `updated_at` |
| `content_items`, `content_item_attachments` | Blog posts and other content with planned, publication and refreshed dates, traffic and CTA clicks | Indexes on each date, `(content_type, publication_date)`, `(status, publication_date)`, `updated_at` |
| `marketing_monthly_reports` | Frozen monthly reports (JSON payload) | **Unique `(report_year, report_month)`**: insert-only |

## History and integrity rules

- **Ranking history** is insert-only per month. A same-month correction is an audited update (current and previous
  month only, any past month for a Super Admin). Earlier months are never overwritten.
- **Targets** are monthly and unique per type and month. History is never overwritten.
- **Recurring occurrences** are unique per activity and period, so the daily job and a completion can never create
  the same period twice. Closed occurrences stay as history.
- **Frozen marketing reports** are insert-only, one per month.
- **Audit entries** are written in their own transaction (`REQUIRES_NEW`), so they survive a failed business
  transaction. Integration tests therefore don't use `@Transactional`.

## Indexing and query checks

Indexes exist for every filter column and every default list order. `QueryBudgetIT` (run with `mvnw verify -Pit`)
calls each dashboard, report, list and detail endpoint, runs `EXPLAIN` on every `SELECT` it sends, and fails when a
table that grows with history is read in full with no usable index. A newest-first page (`ORDER BY` an indexed
column `LIMIT n`) passes, because on a large table MySQL walks the index and stops after the page.

Global search uses contains-matches (`LIKE '%term%'`) that no B-tree index can serve. Each group is limited to five
results and ordered by an indexed column. The knowledge base uses its FULLTEXT index instead.

## Local setup

```sql
CREATE DATABASE team_ops_board CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'teamops'@'localhost' IDENTIFIED BY '<DB_PASSWORD from backend/.env>';
GRANT ALL PRIVILEGES ON team_ops_board.* TO 'teamops'@'localhost';
-- The integration tests' own database (mvnw verify -Pit); they never touch team_ops_board.
CREATE DATABASE team_ops_board_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON team_ops_board_it.* TO 'teamops'@'localhost';
```

Flyway creates and migrates every table when the backend starts (and the integration tests migrate
`team_ops_board_it` the same way). Override the test database with `-Dit.db.url=...`.
