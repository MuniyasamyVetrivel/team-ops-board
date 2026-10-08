-- V11: recurring marketing activities (brief sections 35-36).
-- Each activity produces one occurrence per period (day, ISO week, month, quarter or year) and, when it has a task
-- title template, a task for it. Completing the occurrence (or its task) creates the next one; past occurrences stay as
-- history. Overdue and due-soon are computed from due_date, never stored. Last completed and next due are derived from
-- the occurrences.

CREATE TABLE marketing_activities (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    name                VARCHAR(200)  NOT NULL,
    description         VARCHAR(2000) NULL,
    department_id       BIGINT        NOT NULL,
    owner_id            BIGINT        NULL,
    frequency           VARCHAR(16)   NOT NULL,
    start_date          DATE          NOT NULL,
    end_date            DATE          NULL,
    -- Days after the period start that each occurrence is due (clamped to the period's last day).
    due_offset_days     INT           NOT NULL DEFAULT 0,
    -- e.g. "Update {month} keyword rankings". NULL = occurrences without a task (completed on the activity).
    task_title_template VARCHAR(250)  NULL,
    -- Assignee of generated tasks; the owner when NULL.
    default_assignee_id BIGINT        NULL,
    task_priority       VARCHAR(32)   NOT NULL DEFAULT 'MEDIUM',
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_marketing_activities_active (active, frequency),
    KEY idx_marketing_activities_department (department_id),
    KEY idx_marketing_activities_owner (owner_id),
    KEY idx_marketing_activities_assignee (default_assignee_id),
    CONSTRAINT fk_marketing_activities_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_marketing_activities_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_activities_assignee FOREIGN KEY (default_assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_marketing_activities_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_marketing_activities_offset CHECK (due_offset_days BETWEEN 0 AND 365)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The checklist template, copied into each generated task's checklist.
CREATE TABLE marketing_activity_checklist_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    activity_id BIGINT       NOT NULL,
    content     VARCHAR(500) NOT NULL,
    position    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_marketing_activity_checklist_activity (activity_id, position),
    CONSTRAINT fk_marketing_activity_checklist_activity FOREIGN KEY (activity_id) REFERENCES marketing_activities (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per activity and period (idempotent generation through the unique key).
CREATE TABLE marketing_activity_occurrences (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    activity_id  BIGINT        NOT NULL,
    period_start DATE          NOT NULL,
    period_end   DATE          NOT NULL,
    due_date     DATE          NOT NULL,
    status       VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    task_id      BIGINT        NULL,
    completed_at DATETIME(6)   NULL,
    completed_by BIGINT        NULL,
    notes        VARCHAR(1000) NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_activity_occurrence_period (activity_id, period_start),
    UNIQUE KEY uk_marketing_activity_occurrence_task (task_id),
    KEY idx_marketing_activity_occurrences_due (status, due_date),
    KEY idx_marketing_activity_occurrences_completed_by (completed_by),
    CONSTRAINT fk_marketing_activity_occurrences_activity FOREIGN KEY (activity_id) REFERENCES marketing_activities (id),
    CONSTRAINT fk_marketing_activity_occurrences_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_activity_occurrences_completed_by FOREIGN KEY (completed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_marketing_activity_occurrences_period CHECK (period_end >= period_start AND due_date BETWEEN period_start AND period_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
