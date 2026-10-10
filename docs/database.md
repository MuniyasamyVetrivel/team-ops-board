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
| Timestamps | Tables with both `created_at` and `updated_at` map to `BaseEntity` |
| Optimistic locking | Editable records have a `version` column (`@Version`); stale writes answer 409 `STALE_UPDATE` |
| Derived values | Never stored: rates, remaining, achievement %, target status, workload %, SLA state and project progress are computed in services. The exceptions are deliberate snapshots, such as SLA due times taken at ticket creation and the previous position recorded with each monthly ranking |
| Codes | `TSK-`, `TKT-`, `PRJ-`, `APR-`, `LEAD-`, `BLK-` codes come from `code_sequences` (one row per prefix, read with `SELECT … FOR UPDATE` inside the caller's transaction) |
| Dev data | Never in Flyway. Development data comes from the Java `DevDataSeeder` (`DEV_SEED_ENABLED=true`) |

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

Never edit an applied migration; add `V{n+1}__description.sql` instead, in the phase that first needs it.

## Tables by module

### Identity and organisation

| Table | Purpose | Notable constraints |
|---|---|---|
| `users` | People: names, job title, contact, department, `reports_to_id`, weekly capacity, status, BCrypt hash | Unique `email` |
| `roles`, `permissions` | Role and permission catalogue | Unique `code` |
| `user_roles`, `role_permissions`, `user_permissions` | Role membership, role grants and direct (per-person) grants | Composite primary keys |
| `departments` | Department with optional `manager_id` and status | Unique `code`, unique `name` |
| `department_members` | Secondary memberships (`MEMBER` or `MANAGER`) | Composite primary key |
| `refresh_tokens` | SHA-256 hash of each refresh token, expiry, revocation time, user agent and IP | Unique `token_hash` |
| `audit_logs` | Actor, action, entity, JSON details, IP and user agent | Indexes on `(action, created_at)`, `(actor_id, created_at)`, `(entity_type, entity_id)` |
| `app_settings` | Admin settings (workload window, default task hours, SLA warning %, marketing behind %, upload limit, default capacity) | Unique `setting_key` |
| `code_sequences` | Next number per code prefix | One row per prefix |

### Work management

| Table | Purpose | Notable constraints |
|---|---|---|
| `tasks` | Task with department, project/milestone, assignee, creator, priority, status, dates, estimate/actual hours, source | Unique `code`; indexes on `(assignee_id, status)`, `(department_id, status)`, `(due_date, status)`, `(status, completed_at)`, `created_at`, `completed_at` |
| `task_comments`, `task_checklists`, `task_watchers`, `task_dependencies`, `task_tags` | Task detail collections | — |
| `task_history` | Field-level change history shown on the task | — |
| `files`, `task_attachments` | Stored file metadata and its links to tasks (also used by tickets, projects, KB and content) | Unique `storage_key` |
| `projects`, `project_members`, `project_milestones`, `project_risks`, `project_dependencies`, `project_attachments` | Projects and their parts | Unique project `code` |
| `notifications` | In-app notifications | Unique `(user_id, dedup_key)` so reminders are sent once |
| `calendar_events` | Company, department and personal events and leave | — |

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
```

Flyway creates and migrates every table when the backend starts.
