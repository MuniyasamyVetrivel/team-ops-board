-- V4: tasks and their collaboration tables.
-- Derived values (overdue, workload %, completion counts) are computed, never stored.
-- "A task cannot depend on itself" is enforced in TaskService: MySQL rejects CHECK constraints on columns that
-- cascading foreign keys use.

CREATE TABLE tasks (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(20)   NOT NULL,
    title           VARCHAR(250)  NOT NULL,
    description     TEXT          NULL,
    department_id   BIGINT        NOT NULL,
    project_id      BIGINT        NULL,
    milestone_id    BIGINT        NULL,
    assignee_id     BIGINT        NULL,
    created_by      BIGINT        NULL,
    priority        VARCHAR(32)   NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(32)   NOT NULL DEFAULT 'TODO',
    start_date      DATE          NULL,
    due_date        DATE          NULL,
    estimated_hours DECIMAL(6, 2) NULL,
    actual_hours    DECIMAL(6, 2) NULL,
    completed_at    DATETIME(6)   NULL,
    source          VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tasks_code (code),
    KEY idx_tasks_assignee_status (assignee_id, status),
    KEY idx_tasks_department_status (department_id, status),
    KEY idx_tasks_due_status (due_date, status),
    KEY idx_tasks_project (project_id),
    KEY idx_tasks_milestone (milestone_id),
    KEY idx_tasks_created_by (created_by),
    KEY idx_tasks_completed_at (completed_at),
    CONSTRAINT fk_tasks_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_milestone FOREIGN KEY (milestone_id) REFERENCES project_milestones (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_tasks_hours CHECK ((estimated_hours IS NULL OR estimated_hours >= 0) AND (actual_hours IS NULL OR actual_hours >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_tags (
    task_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (task_id, tag_id),
    KEY idx_task_tags_tag (tag_id),
    CONSTRAINT fk_task_tags_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_comments (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    task_id    BIGINT      NOT NULL,
    author_id  BIGINT      NULL,
    body       TEXT        NOT NULL,
    edited     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_task_comments_task (task_id, created_at),
    CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_attachments (
    task_id  BIGINT      NOT NULL,
    file_id  BIGINT      NOT NULL,
    added_by BIGINT      NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (task_id, file_id),
    KEY idx_task_attachments_file (file_id),
    CONSTRAINT fk_task_attachments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_checklists (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    task_id    BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    is_done    BOOLEAN      NOT NULL DEFAULT FALSE,
    done_by    BIGINT       NULL,
    done_at    DATETIME(6)  NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_task_checklists_task (task_id, position),
    CONSTRAINT fk_task_checklists_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_checklists_done_by FOREIGN KEY (done_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_dependencies (
    task_id            BIGINT NOT NULL,
    depends_on_task_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, depends_on_task_id),
    KEY idx_task_dependencies_target (depends_on_task_id),
    CONSTRAINT fk_task_dependencies_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_dependencies_target FOREIGN KEY (depends_on_task_id) REFERENCES tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_watchers (
    task_id  BIGINT      NOT NULL,
    user_id  BIGINT      NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (task_id, user_id),
    KEY idx_task_watchers_user (user_id),
    CONSTRAINT fk_task_watchers_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_watchers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    task_id    BIGINT       NOT NULL,
    changed_by BIGINT       NULL,
    field_name VARCHAR(60)  NOT NULL,
    old_value  VARCHAR(500) NULL,
    new_value  VARCHAR(500) NULL,
    changed_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_task_history_task (task_id, changed_at),
    CONSTRAINT fk_task_history_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
