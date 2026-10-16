-- V30: backups and "Export all data" (Expansion Phase X3).
--
-- 1. backup_runs: one row per backup attempt (nightly or "Run backup now"): when it started and ended, the file, its
--    size and SHA-256 checksum, where it was stored, and why it failed. removed_at is set when the retention policy
--    (30 daily, 12 monthly) deleted the file; the row stays as history.
-- 2. BACKUP_VIEW (backup status and history) and DATA_EXPORT (download every table as CSV plus all uploads): Super
--    Admin only by default, grantable later from Admin settings. "Run backup now" stays with the Super Admin role.

CREATE TABLE backup_runs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    trigger_type  VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    started_at    DATETIME(6)   NOT NULL,
    finished_at   DATETIME(6)   NULL,
    file_name     VARCHAR(255)  NULL,
    size_bytes    BIGINT        NULL,
    checksum      VARCHAR(64)   NULL,
    target        VARCHAR(500)  NOT NULL,
    upload_files  INT           NULL,
    error_message VARCHAR(2000) NULL,
    requested_by  BIGINT        NULL,
    removed_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_backup_runs_started (started_at),
    KEY idx_backup_runs_status (status, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, name, module, description) VALUES
    ('BACKUP_VIEW', 'View backup status', 'ADMIN',
     'See the backup status page: last backup, next run, history and alerts'),
    ('DATA_EXPORT', 'Export all data', 'ADMIN',
     'Download every table as CSV plus all uploaded files in one ZIP');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('BACKUP_VIEW', 'DATA_EXPORT');
