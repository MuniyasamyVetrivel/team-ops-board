-- V14: content items (brief sections 45-47). Created now because marketing leads (V15) link to the content that
-- brought them in; the content module (CRUD, attachments, monthly metrics) arrives in Phase 18.

CREATE TABLE content_items (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    title               VARCHAR(300)  NOT NULL,
    url                 VARCHAR(1000) NULL,
    content_type        VARCHAR(32)   NOT NULL DEFAULT 'BLOG',
    author_id           BIGINT        NULL,
    owner_id            BIGINT        NULL,
    -- Set once the item is published; content counts in the month of this date.
    publication_date    DATE          NULL,
    target_keyword_id   BIGINT        NULL,
    target_keyword_text VARCHAR(255)  NULL,
    target_page_id      BIGINT        NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'IDEA',
    organic_traffic     INT           NULL,
    cta_clicks          INT           NULL,
    notes               VARCHAR(2000) NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_content_items_status_date (status, publication_date),
    KEY idx_content_items_type_date (content_type, publication_date),
    KEY idx_content_items_owner (owner_id),
    KEY idx_content_items_author (author_id),
    KEY idx_content_items_keyword (target_keyword_id),
    KEY idx_content_items_page (target_page_id),
    CONSTRAINT fk_content_items_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_content_items_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_content_items_keyword FOREIGN KEY (target_keyword_id) REFERENCES marketing_keywords (id) ON DELETE SET NULL,
    CONSTRAINT fk_content_items_page FOREIGN KEY (target_page_id) REFERENCES marketing_pages (id) ON DELETE SET NULL,
    CONSTRAINT ck_content_items_counts CHECK ((organic_traffic IS NULL OR organic_traffic >= 0) AND (cta_clicks IS NULL OR cta_clicks >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
