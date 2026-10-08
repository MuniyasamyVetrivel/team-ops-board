-- V10: monthly marketing targets (brief sections 30-34).
-- Achievement %, remaining and status are computed (TargetProgress), never stored. actual_value is only used for
-- types whose actual is entered by hand; automatic types (actual_source other than MANUAL) aggregate their actual from
-- the underlying records (SEO rankings, pages, and later leads, backlinks, content and campaigns).

CREATE TABLE marketing_target_types (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(50)   NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    description          VARCHAR(500)  NULL,
    unit                 VARCHAR(16)   NOT NULL DEFAULT 'COUNT',
    actual_source        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    -- Only for LEADS_BY_SOURCE: which lead source counts.
    lead_source_filter   VARCHAR(32)   NULL,
    -- Overrides the global marketing.target.behindThresholdPct setting when set.
    behind_threshold_pct DECIMAL(5,2)  NULL,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    position             INT           NOT NULL DEFAULT 0,
    created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_target_types_code (code),
    UNIQUE KEY uk_marketing_target_types_name (name),
    KEY idx_marketing_target_types_active (active, position),
    CONSTRAINT ck_marketing_target_types_threshold CHECK (behind_threshold_pct IS NULL OR behind_threshold_pct BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO marketing_target_types (code, name, unit, actual_source, lead_source_filter, position) VALUES
    ('WEBSITE_LEADS',         'Website Leads',         'COUNT', 'LEADS',           NULL,            1),
    ('ORGANIC_LEADS',         'Organic Leads',         'COUNT', 'LEADS_BY_SOURCE', 'ORGANIC',       2),
    ('BLOG_LEADS',            'Blog Leads',            'COUNT', 'LEADS_BY_SOURCE', 'BLOG',          3),
    ('EMAIL_LEADS',           'Email Leads',           'COUNT', 'LEADS_BY_SOURCE', 'EMAIL',         4),
    ('LINKEDIN_LEADS',        'LinkedIn Leads',        'COUNT', 'LEADS_BY_SOURCE', 'LINKEDIN',      5),
    ('PAID_CAMPAIGN_LEADS',   'Paid Campaign Leads',   'COUNT', 'LEADS_BY_SOURCE', 'PAID_CAMPAIGN', 6),
    ('BACKLINKS',             'Backlinks',             'COUNT', 'BACKLINKS_LIVE',  NULL,            7),
    ('BLOGS_PUBLISHED',       'Blogs Published',       'COUNT', 'BLOGS_PUBLISHED', NULL,            8),
    ('LANDING_PAGES_CREATED', 'Landing Pages Created', 'COUNT', 'LANDING_PAGES',   NULL,            9),
    ('KEYWORDS_TOP10',        'Keywords in Top 10',    'COUNT', 'KEYWORDS_TOP10',  NULL,            10),
    ('MARKETING_PROSPECTS',   'Marketing Prospects',   'COUNT', 'MANUAL',          NULL,            11),
    ('EMAIL_CAMPAIGNS',       'Email Campaigns',       'COUNT', 'EMAIL_CAMPAIGNS', NULL,            12),
    ('LINKEDIN_CAMPAIGNS',    'LinkedIn Campaigns',    'COUNT', 'PAID_CAMPAIGNS',  NULL,            13);

-- Insert-only per type and month: a new month is a new row. Closed months are locked (except to a Super Admin).
CREATE TABLE marketing_targets (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    target_type_id BIGINT         NOT NULL,
    month          INT            NOT NULL,
    year           INT            NOT NULL,
    target_value   DECIMAL(14,2)  NOT NULL,
    -- Entered by hand: for MANUAL types, and for automatic types whose source module does not exist yet.
    actual_value   DECIMAL(14,2)  NULL,
    owner_id       BIGINT         NULL,
    department_id  BIGINT         NOT NULL,
    notes          VARCHAR(1000)  NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_targets_type_month (target_type_id, month, year),
    KEY idx_marketing_targets_period (year, month),
    KEY idx_marketing_targets_owner (owner_id),
    KEY idx_marketing_targets_department (department_id),
    CONSTRAINT fk_marketing_targets_type FOREIGN KEY (target_type_id) REFERENCES marketing_target_types (id),
    CONSTRAINT fk_marketing_targets_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_targets_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_marketing_targets_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_marketing_targets_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_marketing_targets_target CHECK (target_value > 0),
    CONSTRAINT ck_marketing_targets_actual CHECK (actual_value IS NULL OR actual_value >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
