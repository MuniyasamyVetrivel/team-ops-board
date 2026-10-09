-- V18: files attached to content items (brief section 64: attachments for marketing content), e.g. a draft, a brief
-- or the published post's images. Stored through FileService like task, ticket and article attachments.

CREATE TABLE content_item_attachments (
    content_item_id BIGINT      NOT NULL,
    file_id         BIGINT      NOT NULL,
    added_by        BIGINT      NULL,
    added_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (content_item_id, file_id),
    KEY idx_content_item_attachments_file (file_id),
    CONSTRAINT fk_content_item_attachments_item FOREIGN KEY (content_item_id) REFERENCES content_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_content_item_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_content_item_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
