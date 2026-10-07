-- V6: help desk tickets and SLA policies.
-- SLA state, remaining time and compliance are computed in SlaCalculator and never stored. Due times and the warning
-- threshold are snapshotted on the ticket at creation, so editing a policy never changes existing tickets.

CREATE TABLE sla_policies (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(100) NOT NULL,
    priority               VARCHAR(32)  NOT NULL,
    first_response_minutes INT          NOT NULL,
    resolution_minutes     INT          NOT NULL,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_policies_priority (priority),
    CONSTRAINT ck_sla_policies_minutes CHECK (first_response_minutes > 0 AND resolution_minutes >= first_response_minutes)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO sla_policies (name, priority, first_response_minutes, resolution_minutes) VALUES
    ('Urgent', 'URGENT', 60, 240),
    ('High', 'HIGH', 120, 480),
    ('Medium', 'MEDIUM', 240, 1440),
    ('Low', 'LOW', 480, 2880);

CREATE TABLE ticket_categories (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100) NOT NULL,
    description           VARCHAR(500) NULL,
    -- The team that handles tickets in this category; NULL means the requester picks a department.
    default_department_id BIGINT       NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_categories_name (name),
    KEY idx_ticket_categories_department (default_department_id),
    CONSTRAINT fk_ticket_categories_department FOREIGN KEY (default_department_id) REFERENCES departments (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO ticket_categories (name, description, default_department_id)
SELECT c.name, c.description, d.id
FROM (
    SELECT 'IT Support' AS name, 'Laptops, accounts, email and general IT help' AS description, 'IT' AS code
    UNION ALL SELECT 'Access Request', 'Access to systems, folders and tools', 'IT'
    UNION ALL SELECT 'Hardware', 'Broken or missing equipment', 'IT'
    UNION ALL SELECT 'Software', 'Installations, licences and application issues', 'IT'
    UNION ALL SELECT 'Network', 'Wi-Fi, VPN and connectivity', 'IT'
    UNION ALL SELECT 'Security Incident', 'Phishing, suspicious activity and data exposure', 'CYBERSEC'
    UNION ALL SELECT 'HR Query', 'Policies, leave and HR documents', 'HR'
    UNION ALL SELECT 'Payroll', 'Salary, reimbursements and tax', 'PAYROLL'
    UNION ALL SELECT 'Website Issue', 'Bugs or content changes on the company website', 'WEBDEV'
    UNION ALL SELECT 'Marketing Request', 'Campaign, SEO and content requests', 'DM'
    UNION ALL SELECT 'Design Request', 'Creatives, banners and presentations', 'GRAPHICS'
) c
LEFT JOIN departments d ON d.code = c.code;

INSERT INTO ticket_categories (name, description, default_department_id) VALUES
    ('Other', 'Anything else; choose the team that should handle it', NULL);

CREATE TABLE tickets (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    code                  VARCHAR(20)   NOT NULL,
    subject               VARCHAR(250)  NOT NULL,
    description           TEXT          NULL,
    requester_id          BIGINT        NULL,
    -- The department that handles the ticket (not necessarily the requester's).
    department_id         BIGINT        NOT NULL,
    category_id           BIGINT        NULL,
    priority              VARCHAR(32)   NOT NULL DEFAULT 'MEDIUM',
    assignee_id           BIGINT        NULL,
    status                VARCHAR(32)   NOT NULL DEFAULT 'NEW',
    sla_policy_id         BIGINT        NULL,
    -- When the SLA clock started (ticket creation); the budgets are the due times minus this instant.
    sla_start_at          DATETIME(6)   NOT NULL,
    first_response_due_at DATETIME(6)   NOT NULL,
    resolution_due_at     DATETIME(6)   NOT NULL,
    sla_warning_pct       INT           NOT NULL,
    first_responded_at    DATETIME(6)   NULL,
    resolved_at           DATETIME(6)   NULL,
    closed_at             DATETIME(6)   NULL,
    -- Set while the SLA clock is paused (waiting for the requester, or resolved); paused minutes accumulate.
    sla_paused_at         DATETIME(6)   NULL,
    sla_paused_minutes    INT           NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version               INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_code (code),
    KEY idx_tickets_status_priority (status, priority),
    KEY idx_tickets_assignee_status (assignee_id, status),
    KEY idx_tickets_requester_status (requester_id, status),
    KEY idx_tickets_department_status (department_id, status),
    KEY idx_tickets_category (category_id),
    KEY idx_tickets_resolution_due (resolution_due_at),
    KEY idx_tickets_created_at (created_at),
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_tickets_category FOREIGN KEY (category_id) REFERENCES ticket_categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_sla_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policies (id) ON DELETE SET NULL,
    CONSTRAINT ck_tickets_paused CHECK (sla_paused_minutes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ticket_comments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT      NOT NULL,
    author_id   BIGINT      NULL,
    body        TEXT        NOT NULL,
    -- Internal notes are visible to agents only, never to the requester.
    is_internal BOOLEAN     NOT NULL DEFAULT FALSE,
    edited      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ticket_comments_ticket (ticket_id, created_at),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ticket_attachments (
    ticket_id BIGINT      NOT NULL,
    file_id   BIGINT      NOT NULL,
    added_by  BIGINT      NULL,
    added_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ticket_id, file_id),
    KEY idx_ticket_attachments_file (file_id),
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ticket_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id  BIGINT       NOT NULL,
    changed_by BIGINT       NULL,
    field_name VARCHAR(60)  NOT NULL,
    old_value  VARCHAR(500) NULL,
    new_value  VARCHAR(500) NULL,
    changed_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ticket_history_ticket (ticket_id, changed_at),
    CONSTRAINT fk_ticket_history_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
