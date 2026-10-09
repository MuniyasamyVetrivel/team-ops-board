-- V17: the content module (brief sections 47-48) on the content_items table from V14. A blog counts towards the
-- monthly blog target in the month of its publication date while it is live (PUBLISHED or UPDATED); the planned date
-- is the month it is planned for ("how many blogs planned?"), and an UPDATED item keeps the date it was refreshed.
-- Counts, remaining and achievement are computed, never stored.

ALTER TABLE content_items
    ADD COLUMN planned_date   DATE   NULL AFTER owner_id,
    ADD COLUMN refreshed_date DATE   NULL AFTER publication_date,
    ADD COLUMN created_by     BIGINT NULL AFTER notes,
    ADD KEY idx_content_items_planned (planned_date),
    ADD KEY idx_content_items_published (publication_date),
    ADD KEY idx_content_items_refreshed (refreshed_date),
    ADD KEY idx_content_items_url (url(255)),
    ADD CONSTRAINT fk_content_items_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    -- Live content has its publication date; a refresh comes after it.
    ADD CONSTRAINT ck_content_items_live_date CHECK (status NOT IN ('PUBLISHED', 'UPDATED') OR publication_date IS NOT NULL),
    ADD CONSTRAINT ck_content_items_refreshed CHECK (refreshed_date IS NULL OR (publication_date IS NOT NULL AND refreshed_date >= publication_date));
