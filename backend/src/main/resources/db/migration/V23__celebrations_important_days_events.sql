-- Calendar celebrations, important days and marketing events (docs/CALENDAR_FEATURES_BRIEF.md).
-- Birthdays and work anniversaries are computed from the employee's dates every year; occurrences are never stored.

-- Create, edit and delete employees, important days, events and LinkedIn posts. A Digital Marketing permission:
-- granted to people (never to a role) and only together with MARKETING_VIEW. The Super Admin role holds every one.
INSERT INTO permissions (code, name, module, description) VALUES
    ('CELEBRATIONS_EDIT', 'Manage celebrations, important days and events', 'MARKETING',
     'Employees, important days, marketing events and LinkedIn post tracking; sees full birth dates');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'CELEBRATIONS_EDIT' WHERE r.code = 'SUPER_ADMIN';

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('celebrations.reminderDaysBefore', '2', 'INTEGER',
     'Days before a birthday, work anniversary, important day or event that reminders are sent (plus the day itself)');

-- Staff and consultants, most of whom never sign in. Linking an app user is optional.
CREATE TABLE employees (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code     VARCHAR(40)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE         NOT NULL,
    designation       VARCHAR(150) NOT NULL,
    reporting_manager VARCHAR(150) NOT NULL,
    -- Needed for work anniversaries only.
    date_of_joining   DATE         NULL,
    department_id     BIGINT       NULL,
    work_email        VARCHAR(254) NULL,
    office_location   VARCHAR(150) NULL,
    photo_file_id     BIGINT       NULL,
    user_id           BIGINT       NULL,
    -- ACTIVE or LEFT; people who left stop appearing on the calendar and in reminders.
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by        BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_code (employee_code),
    UNIQUE KEY uk_employees_user (user_id),
    KEY idx_employees_status_name (status, first_name, last_name),
    KEY idx_employees_department (department_id),
    KEY idx_employees_photo (photo_file_id),
    KEY idx_employees_created_by (created_by),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_employees_photo FOREIGN KEY (photo_file_id) REFERENCES files (id) ON DELETE SET NULL,
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_employees_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Festivals, national and industry days, company milestones. YEARLY repeats on the same date; ONE_OFF is entered
-- per year (festivals whose date moves, such as Diwali).
CREATE TABLE important_days (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150) NOT NULL,
    day_date        DATE         NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    region          VARCHAR(16)  NOT NULL,
    recurrence      VARCHAR(16)  NOT NULL,
    description     TEXT         NULL,
    marketing_notes TEXT         NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_important_days_name_date_region (name, day_date, region),
    KEY idx_important_days_recurrence_date (recurrence, day_date),
    KEY idx_important_days_date (day_date),
    KEY idx_important_days_created_by (created_by),
    CONSTRAINT fk_important_days_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Upcoming events, webinars, event marketing and email marketing.
CREATE TABLE marketing_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    title             VARCHAR(200) NOT NULL,
    event_type        VARCHAR(32)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    start_at          DATETIME(6)  NOT NULL,
    end_at            DATETIME(6)  NULL,
    location          VARCHAR(255) NULL,
    online_link       VARCHAR(500) NULL,
    registration_link VARCHAR(500) NULL,
    description       TEXT         NULL,
    owner_id          BIGINT       NOT NULL,
    email_campaign_id BIGINT       NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_marketing_events_start (start_at),
    KEY idx_marketing_events_owner (owner_id),
    KEY idx_marketing_events_campaign (email_campaign_id),
    KEY idx_marketing_events_created_by (created_by),
    CONSTRAINT fk_marketing_events_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_marketing_events_campaign FOREIGN KEY (email_campaign_id) REFERENCES email_campaigns (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_events_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_marketing_events_end CHECK (end_at IS NULL OR end_at > start_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- LinkedIn post tracking per occurrence: a birthday or anniversary (subject = employee), an important day or an event
-- on one date. A missing row means "not decided yet" (status Not started).
CREATE TABLE linkedin_posts (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    subject_type    VARCHAR(32)  NOT NULL,
    subject_id      BIGINT       NOT NULL,
    occurrence_date DATE         NOT NULL,
    post_needed     BOOLEAN      NOT NULL,
    owner_id        BIGINT       NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'NOT_STARTED',
    post_date       DATE         NULL,
    post_link       VARCHAR(500) NULL,
    updated_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_linkedin_posts_occurrence (subject_type, subject_id, occurrence_date),
    KEY idx_linkedin_posts_date (occurrence_date),
    KEY idx_linkedin_posts_owner (owner_id),
    KEY idx_linkedin_posts_updated_by (updated_by),
    CONSTRAINT fk_linkedin_posts_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_linkedin_posts_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Creatives for important days and attachments for events.
CREATE TABLE occasion_attachments (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    subject_type VARCHAR(32) NOT NULL,
    subject_id   BIGINT      NOT NULL,
    file_id      BIGINT      NOT NULL,
    added_by     BIGINT      NULL,
    added_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_occasion_attachments_file (file_id),
    KEY idx_occasion_attachments_subject (subject_type, subject_id),
    KEY idx_occasion_attachments_added_by (added_by),
    CONSTRAINT fk_occasion_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_occasion_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
