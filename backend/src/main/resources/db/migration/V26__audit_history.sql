-- V26: audit history on every record (Expansion Phase X1).
--
-- 1. Every record table gets created_at, created_by, updated_at and updated_by. The application stamps them
--    (AuditedEntity); a NULL user means the system (background jobs, migrations). Existing rows are backfilled from
--    the closest column the table already had (author, requester, uploader, ...), otherwise they stay NULL = system.
--    The *_by columns have no foreign keys on purpose: they record who did it, even after that user is removed.
--    Pure join tables (user_roles, task_tags, ...) are not records: changes to them are logged on their owner.
--    audit_logs (append-only, actor_id + created_at) and refresh_tokens (credentials) are left out.
-- 2. audit_logs gets the record's label, its parent record (so a task's timeline includes its comments, checklist
--    and attachments) and a flag for entries that hold finance amounts.
-- 3. FINANCE_VIEW: who may see finance amounts, including in audit details.
--
-- Backfills that touch a table with an ON UPDATE updated_at set updated_at = updated_at, so they keep its value.

-- 1a. Tables that already have created_at and updated_at -------------------------------------------------------

ALTER TABLE announcements ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE announcements SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE approval_types ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE approvals ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE approvals SET created_by = requester_id, updated_by = requester_id, updated_at = updated_at;

ALTER TABLE backlinks ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE backlinks SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE calendar_events ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE calendar_events SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE content_items ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE content_items SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE departments ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE documents ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE documents SET created_by = uploaded_by, updated_by = uploaded_by, updated_at = updated_at;

ALTER TABLE email_campaigns ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE employees ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE employees SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE important_days ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE important_days SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE keyword_ranking_history ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE keyword_ranking_history SET created_by = recorded_by, updated_by = recorded_by, updated_at = updated_at;

ALTER TABLE knowledge_articles ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE knowledge_articles SET created_by = author_id, updated_by = author_id, updated_at = updated_at;

ALTER TABLE linkedin_posts ADD COLUMN created_by BIGINT NULL;
UPDATE linkedin_posts SET created_by = updated_by, updated_at = updated_at;

ALTER TABLE marketing_activities ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_activity_occurrences ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_events ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE marketing_events SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE marketing_keywords ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_leads ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE marketing_leads SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE marketing_monthly_reports ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE marketing_monthly_reports SET created_by = generated_by, updated_by = generated_by, updated_at = updated_at;

ALTER TABLE marketing_pages ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_target_types ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_targets ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE paid_campaign_metrics ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE paid_campaign_metrics SET created_by = recorded_by, updated_by = recorded_by, updated_at = updated_at;

ALTER TABLE paid_campaigns ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE project_risks ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE projects ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE sla_policies ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE task_comments ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE task_comments SET created_by = author_id, updated_by = author_id, updated_at = updated_at;

ALTER TABLE tasks ADD COLUMN updated_by BIGINT NULL AFTER updated_at;
UPDATE tasks SET updated_by = created_by, updated_at = updated_at;

ALTER TABLE ticket_categories ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE ticket_comments ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE ticket_comments SET created_by = author_id, updated_by = author_id, updated_at = updated_at;

ALTER TABLE tickets ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;
UPDATE tickets SET created_by = requester_id, updated_by = requester_id, updated_at = updated_at;

ALTER TABLE users ADD COLUMN created_by BIGINT NULL, ADD COLUMN updated_by BIGINT NULL;

-- 1b. Tables with only some of the columns --------------------------------------------------------------------

ALTER TABLE app_settings
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL;
UPDATE app_settings SET created_at = updated_at, created_by = updated_by, updated_at = updated_at;

ALTER TABLE document_versions
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE document_versions SET created_by = uploaded_by, updated_by = uploaded_by, updated_at = created_at;

ALTER TABLE files
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE files SET created_by = uploaded_by, updated_by = uploaded_by, updated_at = created_at;

ALTER TABLE notifications
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE notifications SET updated_at = COALESCE(read_at, created_at);

ALTER TABLE permissions
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE permissions SET updated_at = created_at;

ALTER TABLE project_milestones
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE project_milestones SET updated_at = COALESCE(completed_at, created_at);

UPDATE roles SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE roles
    ADD COLUMN created_by BIGINT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE tags
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE tags SET updated_at = created_at;

ALTER TABLE task_checklists
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE task_checklists SET updated_at = COALESCE(done_at, created_at), updated_by = done_by;

-- 1c. Tables with none of the columns -------------------------------------------------------------------------

ALTER TABLE approval_steps
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE approval_steps s JOIN approvals a ON a.id = s.approval_id
SET s.created_at = a.created_at, s.created_by = a.requester_id,
    s.updated_at = COALESCE(s.decided_at, a.created_at), s.updated_by = COALESCE(s.decided_by, a.requester_id);

ALTER TABLE approval_type_steps
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE approval_type_steps s JOIN approval_types t ON t.id = s.approval_type_id
SET s.created_at = t.created_at, s.updated_at = t.updated_at;

ALTER TABLE article_attachments
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE article_attachments SET created_at = added_at, created_by = added_by, updated_at = added_at, updated_by = added_by;

ALTER TABLE content_item_attachments
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE content_item_attachments SET created_at = added_at, created_by = added_by, updated_at = added_at, updated_by = added_by;

ALTER TABLE department_members
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE department_members SET created_at = joined_at, updated_at = joined_at;

ALTER TABLE knowledge_categories
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE marketing_activity_checklist_items
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE marketing_activity_checklist_items i JOIN marketing_activities a ON a.id = i.activity_id
SET i.created_at = a.created_at, i.updated_at = a.created_at;

ALTER TABLE occasion_attachments
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE occasion_attachments SET created_at = added_at, created_by = added_by, updated_at = added_at, updated_by = added_by;

ALTER TABLE task_attachments
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE task_attachments SET created_at = added_at, created_by = added_by, updated_at = added_at, updated_by = added_by;

ALTER TABLE task_history
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE task_history SET created_at = changed_at, created_by = changed_by, updated_at = changed_at, updated_by = changed_by;

ALTER TABLE ticket_attachments
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE ticket_attachments SET created_at = added_at, created_by = added_by, updated_at = added_at, updated_by = added_by;

ALTER TABLE ticket_history
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_by BIGINT NULL;
UPDATE ticket_history SET created_at = changed_at, created_by = changed_by, updated_at = changed_at, updated_by = changed_by;

-- 2. audit_logs: record label, parent record and finance flag -------------------------------------------------

ALTER TABLE audit_logs
    ADD COLUMN entity_label VARCHAR(255) NULL AFTER entity_id,
    ADD COLUMN parent_type  VARCHAR(60)  NULL AFTER entity_label,
    ADD COLUMN parent_id    BIGINT       NULL AFTER parent_type,
    ADD COLUMN finance      BOOLEAN      NOT NULL DEFAULT FALSE AFTER details,
    ADD KEY idx_audit_logs_entity_created (entity_type, entity_id, created_at),
    ADD KEY idx_audit_logs_parent_created (parent_type, parent_id, created_at),
    DROP KEY idx_audit_logs_entity;

-- Entries written before this migration that carry money amounts (see AuditCatalog.financePaths).
UPDATE audit_logs SET finance = TRUE
WHERE action IN ('APPROVAL_SUBMITTED', 'PAID_CAMPAIGN_CREATED', 'PAID_CAMPAIGN_UPDATED', 'PAID_RESULTS_RECORDED',
                 'PAID_RESULTS_CORRECTED');

-- 3. Finance visibility ---------------------------------------------------------------------------------------

INSERT INTO permissions (code, name, module, description)
VALUES ('FINANCE_VIEW', 'View finance amounts', 'FINANCE',
        'See money amounts such as budgets, spend and approval amounts, including in audit history');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.code = 'FINANCE_VIEW';
