-- V5: in-app notifications and calendar events.
-- Task deadlines (and, later, milestones and approval due dates) are merged into the calendar at query time and are
-- never copied into calendar_events.

CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(500) NULL,
    entity_type VARCHAR(40)  NULL,
    entity_id   BIGINT       NULL,
    -- Set for scheduled reminders (e.g. TASK_OVERDUE:42:2026-10-07) so a reminder is sent once per task and due date.
    dedup_key   VARCHAR(120) NULL,
    read_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_dedup (user_id, dedup_key),
    KEY idx_notifications_user_read (user_id, read_at),
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE calendar_events (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    title         VARCHAR(200) NOT NULL,
    description   TEXT         NULL,
    event_type    VARCHAR(32)  NOT NULL,
    -- UTC instants. All-day events span business-zone midnights: [start of first day, start of the day after the last).
    start_at      DATETIME(6)  NOT NULL,
    end_at        DATETIME(6)  NOT NULL,
    all_day       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- NULL department = company-wide.
    department_id BIGINT       NULL,
    -- The person on leave (LEAVE events).
    user_id       BIGINT       NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_calendar_events_range (start_at, end_at),
    KEY idx_calendar_events_department (department_id),
    KEY idx_calendar_events_user (user_id),
    KEY idx_calendar_events_created_by (created_by),
    CONSTRAINT fk_calendar_events_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_events_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
