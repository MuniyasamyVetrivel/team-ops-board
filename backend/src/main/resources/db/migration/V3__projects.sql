-- V3: projects. Tasks reference projects, so this ships with Phase 5; the project UI arrives in Phase 8.
-- Note: MySQL rejects CHECK constraints on columns used by cascading foreign keys, so rules such as
-- "a project cannot depend on itself" are enforced in the service layer.

CREATE TABLE projects (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(200) NOT NULL,
    description       TEXT         NULL,
    owner_id          BIGINT       NULL,
    department_id     BIGINT       NOT NULL,
    start_date        DATE         NULL,
    end_date          DATE         NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PLANNING',
    progress_override INT          NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_code (code),
    KEY idx_projects_department_status (department_id, status),
    KEY idx_projects_owner (owner_id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_projects_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_projects_progress CHECK (progress_override IS NULL OR progress_override BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_members (
    project_id   BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    project_role VARCHAR(50) NULL,
    added_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, user_id),
    KEY idx_project_members_user (user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_milestones (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    project_id   BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    due_date     DATE         NULL,
    completed_at DATETIME(6)  NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PLANNED',
    position     INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_project_milestones_project (project_id, position),
    KEY idx_project_milestones_due (due_date),
    CONSTRAINT fk_project_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_risks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    probability VARCHAR(32)  NOT NULL DEFAULT 'MEDIUM',
    impact      VARCHAR(32)  NOT NULL DEFAULT 'MEDIUM',
    mitigation  TEXT         NULL,
    owner_id    BIGINT       NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_project_risks_project (project_id),
    CONSTRAINT fk_project_risks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_risks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_dependencies (
    project_id            BIGINT NOT NULL,
    depends_on_project_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, depends_on_project_id),
    KEY idx_project_dependencies_target (depends_on_project_id),
    CONSTRAINT fk_project_dependencies_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_dependencies_target FOREIGN KEY (depends_on_project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_attachments (
    project_id BIGINT      NOT NULL,
    file_id    BIGINT      NOT NULL,
    added_by   BIGINT      NULL,
    added_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, file_id),
    KEY idx_project_attachments_file (file_id),
    CONSTRAINT fk_project_attachments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
