-- Phase 21: admin settings, role permission matrix, audit log search.

-- Settings, approval types and roles become editable records: optimistic locking like every other editable table.
ALTER TABLE app_settings
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE approval_types
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE roles
    ADD COLUMN version    INT         NOT NULL DEFAULT 0,
    ADD COLUMN updated_at DATETIME(6) NULL;

-- Capacity default for new users (was a constant in UserService).
INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('users.defaultWeeklyCapacityHours', '40', 'DECIMAL', 'Weekly capacity in hours given to new users');

-- Audit log viewer: newest-first listing filtered by action or actor.
CREATE INDEX idx_audit_logs_action_created ON audit_logs (action, created_at);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_id, created_at);
