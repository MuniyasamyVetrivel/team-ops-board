-- V16: backlinks (brief sections 45-46). A backlink moves through PROSPECTED, SUBMITTED, APPROVED, LIVE and ends
-- REJECTED or LOST. Each stage it reached keeps its date, and the monthly tracking counts every stage in the month of
-- its date (submitted, approved, live, rejected, lost); the Backlinks target counts links that went live in the month.
-- Counts, remaining and achievement are computed, never stored.

CREATE TABLE backlinks (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    code             VARCHAR(20)   NOT NULL,
    -- The page the link points to: one of our SEO pages, or any URL / site path.
    target_page_id   BIGINT        NULL,
    target_url       VARCHAR(500)  NOT NULL,
    referring_domain VARCHAR(255)  NOT NULL,
    -- The page on the referring site that carries the link; unknown while prospected.
    link_url         VARCHAR(700)  NULL,
    anchor_text      VARCHAR(255)  NULL,
    link_type        VARCHAR(32)   NOT NULL DEFAULT 'GUEST_POST',
    status           VARCHAR(32)   NOT NULL DEFAULT 'PROSPECTED',
    submitted_date   DATE          NULL,
    approved_date    DATE          NULL,
    live_date        DATE          NULL,
    rejected_date    DATE          NULL,
    lost_date        DATE          NULL,
    owner_id         BIGINT        NULL,
    domain_authority INT           NULL,
    notes            VARCHAR(2000) NULL,
    provider         VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    created_by       BIGINT        NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_backlinks_code (code),
    -- One index per stage date: the monthly counts are range scans on each.
    KEY idx_backlinks_submitted (submitted_date),
    KEY idx_backlinks_approved (approved_date),
    KEY idx_backlinks_live (live_date),
    KEY idx_backlinks_rejected (rejected_date),
    KEY idx_backlinks_lost (lost_date),
    KEY idx_backlinks_status (status),
    KEY idx_backlinks_owner (owner_id),
    KEY idx_backlinks_domain (referring_domain),
    KEY idx_backlinks_link_url (link_url(255)),
    KEY idx_backlinks_page (target_page_id),
    CONSTRAINT fk_backlinks_page FOREIGN KEY (target_page_id) REFERENCES marketing_pages (id) ON DELETE SET NULL,
    CONSTRAINT fk_backlinks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_backlinks_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_backlinks_domain_authority CHECK (domain_authority IS NULL OR domain_authority BETWEEN 0 AND 100),
    -- Stages happen in order (the service checks which dates each status needs).
    CONSTRAINT ck_backlinks_date_order CHECK (
        (approved_date IS NULL OR (submitted_date IS NOT NULL AND approved_date >= submitted_date))
        AND (live_date IS NULL OR (submitted_date IS NOT NULL AND live_date >= submitted_date))
        AND (live_date IS NULL OR approved_date IS NULL OR live_date >= approved_date)
        AND (rejected_date IS NULL OR (submitted_date IS NOT NULL AND rejected_date >= submitted_date))
        AND (lost_date IS NULL OR (live_date IS NOT NULL AND lost_date >= live_date)))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO code_sequences (name, prefix, pad_length, next_value) VALUES ('BACKLINK', 'BLK', 6, 1);
