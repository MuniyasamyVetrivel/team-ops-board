-- V12: email campaigns (brief sections 37-39). Paid campaigns get their own migration in Phase 15.
-- Only raw counts are stored; delivery, open, click, click-to-open, lead conversion, bounce and unsubscribe rates are
-- computed (MarketingMath). Counts belong to SENT campaigns; a campaign's month is the month of campaign_date.
-- provider + external_id identify a campaign in an external tool (Zoho Campaigns later), so re-imports never duplicate.

CREATE TABLE email_campaigns (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(200)  NOT NULL,
    campaign_type   VARCHAR(32)   NOT NULL,
    campaign_date   DATE          NOT NULL,
    owner_id        BIGINT        NULL,
    audience        VARCHAR(200)  NULL,
    emails_sent     INT           NOT NULL DEFAULT 0,
    delivered       INT           NOT NULL DEFAULT 0,
    bounced         INT           NOT NULL DEFAULT 0,
    opened          INT           NOT NULL DEFAULT 0,
    unique_opens    INT           NOT NULL DEFAULT 0,
    clicked         INT           NOT NULL DEFAULT 0,
    unique_clicks   INT           NOT NULL DEFAULT 0,
    unsubscribed    INT           NOT NULL DEFAULT 0,
    leads_generated INT           NOT NULL DEFAULT 0,
    status          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    notes           VARCHAR(2000) NULL,
    provider        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    external_id     VARCHAR(100)  NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_campaigns_external (provider, external_id),
    KEY idx_email_campaigns_status_date (status, campaign_date),
    KEY idx_email_campaigns_date (campaign_date),
    KEY idx_email_campaigns_owner (owner_id),
    CONSTRAINT fk_email_campaigns_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_email_campaigns_non_negative CHECK (emails_sent >= 0 AND delivered >= 0 AND bounced >= 0 AND opened >= 0
        AND unique_opens >= 0 AND clicked >= 0 AND unique_clicks >= 0 AND unsubscribed >= 0 AND leads_generated >= 0),
    CONSTRAINT ck_email_campaigns_consistent CHECK (delivered <= emails_sent AND bounced <= emails_sent
        AND unique_opens <= opened AND unique_opens <= delivered AND unique_clicks <= clicked
        AND unique_clicks <= delivered AND unsubscribed <= delivered)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
