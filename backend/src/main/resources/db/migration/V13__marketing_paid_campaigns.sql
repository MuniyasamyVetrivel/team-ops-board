-- V13: paid campaigns, LinkedIn first (brief sections 40-42 and 76).
-- A campaign holds the plan (dates, budget); its results are recorded per month in paid_campaign_metrics, so month
-- filters stay right for campaigns that span months. Campaign totals are sums; CTR, CPL, conversion rate, remaining
-- budget and budget used are computed, never stored.

CREATE TABLE paid_campaigns (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200)  NOT NULL,
    platform    VARCHAR(32)   NOT NULL DEFAULT 'LINKEDIN',
    objective   VARCHAR(32)   NOT NULL,
    start_date  DATE          NOT NULL,
    end_date    DATE          NULL,
    budget      DECIMAL(14,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL DEFAULT 'INR',
    owner_id    BIGINT        NULL,
    status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    notes       VARCHAR(2000) NULL,
    provider    VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(100)  NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paid_campaigns_external (provider, external_id),
    KEY idx_paid_campaigns_dates (start_date, end_date),
    KEY idx_paid_campaigns_status (status, platform),
    KEY idx_paid_campaigns_owner (owner_id),
    CONSTRAINT fk_paid_campaigns_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_paid_campaigns_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_paid_campaigns_budget CHECK (budget > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per campaign and month. A new month is a new row; an open month's row can be corrected (audited).
CREATE TABLE paid_campaign_metrics (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    -- A campaign with recorded months cannot be deleted.
    campaign_id  BIGINT        NOT NULL,
    month        INT           NOT NULL,
    year         INT           NOT NULL,
    amount_spent DECIMAL(14,2) NOT NULL DEFAULT 0,
    impressions  INT           NOT NULL DEFAULT 0,
    clicks       INT           NOT NULL DEFAULT 0,
    leads        INT           NOT NULL DEFAULT 0,
    conversions  INT           NOT NULL DEFAULT 0,
    notes        VARCHAR(1000) NULL,
    source       VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    recorded_by  BIGINT        NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_paid_campaign_metrics_month (campaign_id, month, year),
    KEY idx_paid_campaign_metrics_period (year, month),
    KEY idx_paid_campaign_metrics_recorded_by (recorded_by),
    CONSTRAINT fk_paid_campaign_metrics_campaign FOREIGN KEY (campaign_id) REFERENCES paid_campaigns (id),
    CONSTRAINT fk_paid_campaign_metrics_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_paid_campaign_metrics_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_paid_campaign_metrics_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_paid_campaign_metrics_non_negative CHECK (amount_spent >= 0 AND impressions >= 0 AND clicks >= 0
        AND leads >= 0 AND conversions >= 0),
    CONSTRAINT ck_paid_campaign_metrics_consistent CHECK (clicks <= impressions AND conversions <= leads)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
