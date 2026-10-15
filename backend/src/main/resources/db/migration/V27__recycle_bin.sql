-- V27: recycle bin (Expansion Phase X2).
--
-- 1. Records people delete from the app are soft-deleted: deleted_at and deleted_by are set and the row stays, so it
--    can be restored exactly as it was, children included (comments, checklist, attachments, versions stay untouched).
--    Hibernate hides rows with a deleted_at from every query (@SQLRestriction). Unique keys still cover deleted rows,
--    so a unique value (an employee ID, a page URL) is released only when the row is permanently deleted.
-- 2. recycle_bin_items lists what is in the bin, with the module and department it belonged to, for the Recycle Bin
--    page (Super Admin: everything; Department Managers: their departments).
-- 3. recycleBin.retentionDays: items are permanently deleted automatically after this many days (default 90).

ALTER TABLE tasks
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_tasks_deleted (deleted_at);
ALTER TABLE announcements
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_announcements_deleted (deleted_at);
ALTER TABLE calendar_events
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_calendar_events_deleted (deleted_at);
ALTER TABLE employees
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_employees_deleted (deleted_at);
ALTER TABLE important_days
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_important_days_deleted (deleted_at);
ALTER TABLE marketing_events
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_events_deleted (deleted_at);
ALTER TABLE documents
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_documents_deleted (deleted_at);
ALTER TABLE marketing_activities
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_activities_deleted (deleted_at);
ALTER TABLE backlinks
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_backlinks_deleted (deleted_at);
ALTER TABLE content_items
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_content_items_deleted (deleted_at);
ALTER TABLE email_campaigns
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_email_campaigns_deleted (deleted_at);
ALTER TABLE marketing_leads
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_leads_deleted (deleted_at);
ALTER TABLE paid_campaigns
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_paid_campaigns_deleted (deleted_at);
ALTER TABLE marketing_keywords
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_keywords_deleted (deleted_at);
ALTER TABLE marketing_pages
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_pages_deleted (deleted_at);
ALTER TABLE marketing_targets
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_targets_deleted (deleted_at);
ALTER TABLE marketing_target_types
    ADD COLUMN deleted_at DATETIME(6) NULL, ADD COLUMN deleted_by BIGINT NULL, ADD KEY idx_marketing_target_types_deleted (deleted_at);

-- What is in the bin. One row per deleted record; removed on restore or permanent delete.
CREATE TABLE recycle_bin_items (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     BIGINT       NOT NULL,
    label         VARCHAR(255) NULL,
    module        VARCHAR(40)  NOT NULL,
    department_id BIGINT       NULL,
    deleted_at    DATETIME(6)  NOT NULL,
    deleted_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recycle_bin_items_record (entity_type, entity_id),
    KEY idx_recycle_bin_items_deleted (deleted_at),
    KEY idx_recycle_bin_items_department (department_id, deleted_at),
    KEY idx_recycle_bin_items_module (module, deleted_at),
    KEY idx_recycle_bin_items_deleted_by (deleted_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('recycleBin.retentionDays', '90', 'INTEGER',
     'Days a deleted item stays in the recycle bin before it is permanently deleted automatically');
