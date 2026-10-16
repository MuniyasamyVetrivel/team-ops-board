-- V34: Project Allocation and Bench Management, part 1 (Expansion Phase X6): consultants, clients and client
-- engagements, as the "bench" department tool (X5 module framework).
--
-- 1. bench_consultants: an Employee record (celebrations, employees) on the consultant bench, with experience,
--    location, availability date and employment status (ACTIVE, LEAVE_OF_ABSENCE, NOTICE_PERIOD, LEFT). Allocated,
--    partially allocated and on bench are never stored: they follow from the allocations (part 2). One consultant per
--    employee; the employee is purged only after its consultant is.
-- 2. bench_consultant_skills and bench_consultant_certifications: children of a consultant; they stay untouched while
--    the consultant is in the recycle bin and go with its permanent delete.
-- 3. bench_clients and bench_client_engagements: clients and their engagements (separate from internal Projects).
--    Engagement status (upcoming, active, ended) follows from its dates and is never stored.
-- 4. BENCH_VIEW and BENCH_EDIT, on SUPER_ADMIN only (grantable later from Admin settings), and the module switched on
--    for HR and TALENT_ACQUISITION where those departments exist.

CREATE TABLE bench_consultants (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id       BIGINT       NOT NULL,
    experience_years  DECIMAL(4,1) NULL,
    location          VARCHAR(150) NULL,
    available_from    DATE         NULL,
    employment_status VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    notes             TEXT         NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by        BIGINT       NULL,
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by        BIGINT       NULL,
    deleted_at        DATETIME(6)  NULL,
    deleted_by        BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bench_consultants_employee (employee_id),
    KEY idx_bench_consultants_status (employment_status),
    KEY idx_bench_consultants_available (available_from),
    KEY idx_bench_consultants_deleted (deleted_at),
    CONSTRAINT fk_bench_consultants_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bench_consultant_skills (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    consultant_id BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    position      INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bench_consultant_skills (consultant_id, name),
    KEY idx_bench_consultant_skills_name (name),
    CONSTRAINT fk_bench_consultant_skills_consultant FOREIGN KEY (consultant_id) REFERENCES bench_consultants (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bench_consultant_certifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    consultant_id BIGINT       NOT NULL,
    name          VARCHAR(200) NOT NULL,
    issuer        VARCHAR(150) NULL,
    issued_on     DATE         NULL,
    expires_on    DATE         NULL,
    credential_id VARCHAR(100) NULL,
    position      INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_bench_consultant_certifications_consultant (consultant_id),
    KEY idx_bench_consultant_certifications_expires (expires_on),
    CONSTRAINT fk_bench_consultant_certifications_consultant FOREIGN KEY (consultant_id)
        REFERENCES bench_consultants (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bench_clients (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(200) NOT NULL,
    location   VARCHAR(150) NULL,
    -- ACTIVE or INACTIVE (no current business); set by people, not derived.
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    notes      TEXT         NULL,
    version    INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by BIGINT       NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bench_clients_name (name),
    KEY idx_bench_clients_status (status),
    KEY idx_bench_clients_deleted (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bench_client_engagements (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    client_id          BIGINT       NOT NULL,
    name               VARCHAR(200) NOT NULL,
    location           VARCHAR(150) NULL,
    start_date         DATE         NOT NULL,
    -- Open-ended when NULL.
    end_date           DATE         NULL,
    account_manager_id BIGINT       NULL,
    notes              TEXT         NULL,
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by         BIGINT       NULL,
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by         BIGINT       NULL,
    deleted_at         DATETIME(6)  NULL,
    deleted_by         BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bench_client_engagements_client_name (client_id, name),
    KEY idx_bench_client_engagements_dates (start_date, end_date),
    KEY idx_bench_client_engagements_manager (account_manager_id),
    KEY idx_bench_client_engagements_deleted (deleted_at),
    CONSTRAINT fk_bench_client_engagements_client FOREIGN KEY (client_id) REFERENCES bench_clients (id),
    CONSTRAINT fk_bench_client_engagements_manager FOREIGN KEY (account_manager_id) REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, name, module, description) VALUES
    ('BENCH_VIEW', 'View Bench Management', 'BENCH', 'Open Bench Management: consultants, clients, engagements, bench'),
    ('BENCH_EDIT', 'Edit Bench Management', 'BENCH', 'Change consultants, clients, engagements and allocations');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('BENCH_VIEW', 'BENCH_EDIT');

INSERT INTO department_modules (module_key, department_id, enabled)
SELECT 'bench', d.id, TRUE FROM departments d WHERE d.code IN ('HR', 'TALENT_ACQUISITION');
