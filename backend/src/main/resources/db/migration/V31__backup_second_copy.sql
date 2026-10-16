-- V31: an optional second copy of every backup (Expansion Phase X3, BACKUP_SECONDARY_TARGET).
--
-- The existing target, status, error_message and removed_at columns describe the first copy. The secondary_* columns
-- describe the second copy of the same file: where it went, whether it was stored and verified, why it failed, and
-- when the retention policy deleted it. secondary_status is NULL for runs made while no second target was configured.

ALTER TABLE backup_runs
    ADD COLUMN secondary_target     VARCHAR(500)  NULL AFTER error_message,
    ADD COLUMN secondary_status     VARCHAR(20)   NULL AFTER secondary_target,
    ADD COLUMN secondary_error      VARCHAR(2000) NULL AFTER secondary_status,
    ADD COLUMN secondary_removed_at DATETIME(6)   NULL AFTER removed_at,
    ADD KEY idx_backup_runs_secondary_status (secondary_status, started_at);
