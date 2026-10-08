-- V9: SEO pages, keywords and monthly ranking history (brief sections 23-28, 56).
-- A NULL position means Not Ranked. Ranking status (TOP 10 / RANKING / NOT RANKED), movement and every page
-- statistic are computed from the history, never stored. marketing_keywords.current_position, previous_position and
-- last_ranked_at are a cache of the latest history rows; the history stays the source of truth.

CREATE TABLE marketing_pages (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    -- A site path (/services/sap-testing) or a full http(s) URL.
    url             VARCHAR(500) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    page_type       VARCHAR(32)  NOT NULL,
    primary_keyword VARCHAR(200) NULL,
    department_id   BIGINT       NOT NULL,
    owner_id        BIGINT       NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_pages_url (url),
    KEY idx_marketing_pages_status_type (status, page_type),
    KEY idx_marketing_pages_department (department_id),
    KEY idx_marketing_pages_owner (owner_id),
    CONSTRAINT fk_marketing_pages_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_marketing_pages_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE marketing_keywords (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    -- A page with keywords cannot be deleted (archive it instead).
    page_id            BIGINT       NOT NULL,
    keyword            VARCHAR(200) NOT NULL,
    search_engine      VARCHAR(32)  NOT NULL DEFAULT 'GOOGLE',
    location           VARCHAR(100) NOT NULL DEFAULT 'India',
    device             VARCHAR(16)  NOT NULL DEFAULT 'DESKTOP',
    target_position    INT          NULL,
    current_position   INT          NULL,
    previous_position  INT          NULL,
    search_volume      INT          NULL,
    keyword_difficulty INT          NULL,
    owner_id           BIGINT       NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    last_ranked_at     DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_keywords_identity (page_id, keyword, search_engine, location, device),
    KEY idx_marketing_keywords_owner (owner_id),
    KEY idx_marketing_keywords_status (status),
    KEY idx_marketing_keywords_current (current_position),
    CONSTRAINT fk_marketing_keywords_page FOREIGN KEY (page_id) REFERENCES marketing_pages (id),
    CONSTRAINT fk_marketing_keywords_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_marketing_keywords_target CHECK (target_position IS NULL OR target_position BETWEEN 1 AND 100),
    CONSTRAINT ck_marketing_keywords_current CHECK (current_position IS NULL OR current_position BETWEEN 1 AND 100),
    CONSTRAINT ck_marketing_keywords_previous CHECK (previous_position IS NULL OR previous_position BETWEEN 1 AND 100),
    CONSTRAINT ck_marketing_keywords_volume CHECK (search_volume IS NULL OR search_volume >= 0),
    CONSTRAINT ck_marketing_keywords_difficulty CHECK (keyword_difficulty IS NULL OR keyword_difficulty BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Insert-only per month: a new month is a new row and earlier months are never touched. A correction to the same
-- month updates that row and is audited. previous_position and ranking_change are a snapshot taken when the month
-- was recorded.
CREATE TABLE keyword_ranking_history (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    -- A keyword with ranking history cannot be deleted (archive it instead).
    keyword_id        BIGINT        NOT NULL,
    -- The keyword's page when the month was recorded.
    page_id           BIGINT        NOT NULL,
    ranking_month     INT           NOT NULL,
    ranking_year      INT           NOT NULL,
    ranking_position  INT           NULL,
    previous_position INT           NULL,
    ranking_change    INT           NULL,
    search_volume     INT           NULL,
    notes             VARCHAR(1000) NULL,
    source            VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    recorded_by       BIGINT        NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_keyword_ranking_month (keyword_id, ranking_month, ranking_year),
    KEY idx_keyword_ranking_period (ranking_year, ranking_month),
    KEY idx_keyword_ranking_page (page_id, ranking_year, ranking_month),
    KEY idx_keyword_ranking_recorded_by (recorded_by),
    CONSTRAINT fk_keyword_ranking_keyword FOREIGN KEY (keyword_id) REFERENCES marketing_keywords (id),
    CONSTRAINT fk_keyword_ranking_page FOREIGN KEY (page_id) REFERENCES marketing_pages (id),
    CONSTRAINT fk_keyword_ranking_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_keyword_ranking_month CHECK (ranking_month BETWEEN 1 AND 12),
    CONSTRAINT ck_keyword_ranking_year CHECK (ranking_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_keyword_ranking_position CHECK (ranking_position IS NULL OR ranking_position BETWEEN 1 AND 100),
    CONSTRAINT ck_keyword_ranking_previous CHECK (previous_position IS NULL OR previous_position BETWEEN 1 AND 100),
    CONSTRAINT ck_keyword_ranking_volume CHECK (search_volume IS NULL OR search_volume >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
