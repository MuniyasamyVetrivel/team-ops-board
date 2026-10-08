-- V15: marketing leads (brief sections 43-44). A lead counts in the month of its lead date, towards the Website
-- Leads target and the target of its source. It may name the email campaign, paid campaign or content item that
-- brought it in; which link fits which source is checked by the service.

CREATE TABLE marketing_leads (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    company           VARCHAR(200)  NULL,
    email             VARCHAR(255)  NULL,
    phone             VARCHAR(50)   NULL,
    source            VARCHAR(32)   NOT NULL,
    -- Campaigns and content with leads cannot be deleted (RESTRICT): the attribution is history.
    email_campaign_id BIGINT        NULL,
    paid_campaign_id  BIGINT        NULL,
    content_item_id   BIGINT        NULL,
    department_id     BIGINT        NULL,
    lead_date         DATE          NOT NULL,
    status            VARCHAR(32)   NOT NULL DEFAULT 'NEW',
    owner_id          BIGINT        NULL,
    notes             VARCHAR(2000) NULL,
    provider          VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    external_id       VARCHAR(100)  NULL,
    created_by        BIGINT        NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_leads_code (code),
    UNIQUE KEY uk_marketing_leads_external (provider, external_id),
    KEY idx_marketing_leads_date_source (lead_date, source),
    KEY idx_marketing_leads_status (status, lead_date),
    KEY idx_marketing_leads_owner (owner_id),
    KEY idx_marketing_leads_email (email),
    KEY idx_marketing_leads_email_campaign (email_campaign_id),
    KEY idx_marketing_leads_paid_campaign (paid_campaign_id),
    KEY idx_marketing_leads_content (content_item_id),
    KEY idx_marketing_leads_department (department_id),
    CONSTRAINT fk_marketing_leads_email_campaign FOREIGN KEY (email_campaign_id) REFERENCES email_campaigns (id) ON DELETE RESTRICT,
    CONSTRAINT fk_marketing_leads_paid_campaign FOREIGN KEY (paid_campaign_id) REFERENCES paid_campaigns (id) ON DELETE RESTRICT,
    CONSTRAINT fk_marketing_leads_content FOREIGN KEY (content_item_id) REFERENCES content_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_marketing_leads_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_leads_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_marketing_leads_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    -- At most one link: the campaign or content that brought the lead in.
    CONSTRAINT ck_marketing_leads_one_link CHECK ((email_campaign_id IS NOT NULL) + (paid_campaign_id IS NOT NULL) + (content_item_id IS NOT NULL) <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
