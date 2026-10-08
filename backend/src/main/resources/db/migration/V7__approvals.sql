-- V7: approval workflows (brief section 14).
-- A type's steps are the configurable template; on submit they are copied into approval_steps with the approver
-- resolved, so changing a workflow never alters requests already in flight.

CREATE TABLE approval_types (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NULL,
    requires_amount BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_types_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE approval_type_steps (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    approval_type_id BIGINT      NOT NULL,
    step_order       INT         NOT NULL,
    -- DEPARTMENT_MANAGER (the requester's department manager), ROLE (anyone holding the role) or USER.
    approver_kind    VARCHAR(32) NOT NULL,
    approver_role_id BIGINT      NULL,
    approver_user_id BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_type_steps_order (approval_type_id, step_order),
    KEY idx_approval_type_steps_role (approver_role_id),
    KEY idx_approval_type_steps_user (approver_user_id),
    CONSTRAINT fk_approval_type_steps_type FOREIGN KEY (approval_type_id) REFERENCES approval_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_type_steps_role FOREIGN KEY (approver_role_id) REFERENCES roles (id),
    CONSTRAINT fk_approval_type_steps_user FOREIGN KEY (approver_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE approvals (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    code             VARCHAR(20)   NOT NULL,
    approval_type_id BIGINT        NOT NULL,
    title            VARCHAR(250)  NOT NULL,
    description      TEXT          NULL,
    requester_id     BIGINT        NULL,
    department_id    BIGINT        NOT NULL,
    amount           DECIMAL(14,2) NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'INR',
    due_date         DATE          NULL,
    status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    current_step     INT           NULL,
    decided_at       DATETIME(6)   NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approvals_code (code),
    KEY idx_approvals_status (status, created_at),
    KEY idx_approvals_requester_status (requester_id, status),
    KEY idx_approvals_department_status (department_id, status),
    KEY idx_approvals_type (approval_type_id),
    KEY idx_approvals_due (due_date),
    CONSTRAINT fk_approvals_type FOREIGN KEY (approval_type_id) REFERENCES approval_types (id),
    CONSTRAINT fk_approvals_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_approvals_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_approvals_amount CHECK (amount IS NULL OR amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE approval_steps (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    approval_id      BIGINT      NOT NULL,
    step_order       INT         NOT NULL,
    approver_kind    VARCHAR(32) NOT NULL,
    -- Resolved approver for DEPARTMENT_MANAGER / USER steps; ROLE steps can be decided by any holder of the role.
    approver_id      BIGINT      NULL,
    approver_role_id BIGINT      NULL,
    -- WAITING (not reached), PENDING (current), APPROVED, REJECTED, SKIPPED.
    status           VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    decided_by       BIGINT      NULL,
    comment          TEXT        NULL,
    decided_at       DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_steps_order (approval_id, step_order),
    KEY idx_approval_steps_approver_status (approver_id, status),
    KEY idx_approval_steps_role_status (approver_role_id, status),
    CONSTRAINT fk_approval_steps_approval FOREIGN KEY (approval_id) REFERENCES approvals (id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_steps_approver FOREIGN KEY (approver_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_approval_steps_role FOREIGN KEY (approver_role_id) REFERENCES roles (id),
    CONSTRAINT fk_approval_steps_decided_by FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO approval_types (code, name, description, requires_amount) VALUES
    ('ACCESS', 'Access request', 'Access to systems, folders or tools', FALSE),
    ('SOFTWARE', 'Software request', 'New software or licences', FALSE),
    ('PURCHASE', 'Purchase', 'Equipment and other purchases', TRUE),
    ('EXPENSE', 'Expense claim', 'Reimbursement of business expenses', TRUE),
    ('MARKETING_CREATIVE', 'Marketing creative', 'Sign-off for ads, banners and campaign creatives', FALSE),
    ('RECRUITMENT', 'Recruitment', 'Approval to open a position or make an offer', FALSE),
    ('OTHER', 'Other', 'Anything else that needs a manager''s approval', FALSE);

-- Default workflows: every request starts with the requester's department manager; money and hiring also need a
-- Super Admin.
INSERT INTO approval_type_steps (approval_type_id, step_order, approver_kind)
SELECT id, 1, 'DEPARTMENT_MANAGER' FROM approval_types;

INSERT INTO approval_type_steps (approval_type_id, step_order, approver_kind, approver_role_id)
SELECT t.id, 2, 'ROLE', r.id
FROM approval_types t JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE t.code IN ('PURCHASE', 'EXPENSE', 'RECRUITMENT', 'SOFTWARE');
