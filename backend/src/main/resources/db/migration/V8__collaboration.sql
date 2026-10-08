-- V8: announcements, knowledge base and documents (brief sections 15-17).
-- Read/acknowledgement rates and article counts are computed, never stored (except the article view counter).

CREATE TABLE announcements (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    title                VARCHAR(200) NOT NULL,
    body                 TEXT         NOT NULL,
    -- NULL = everyone.
    target_department_id BIGINT       NULL,
    priority             VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',
    publish_at           DATETIME(6)  NOT NULL,
    expires_at           DATETIME(6)  NULL,
    ack_required         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by           BIGINT       NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version              INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_announcements_publish (publish_at, expires_at),
    KEY idx_announcements_department (target_department_id),
    KEY idx_announcements_created_by (created_by),
    CONSTRAINT fk_announcements_department FOREIGN KEY (target_department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE announcement_reads (
    announcement_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    read_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged_at DATETIME(6) NULL,
    PRIMARY KEY (announcement_id, user_id),
    KEY idx_announcement_reads_user (user_id),
    CONSTRAINT fk_announcement_reads_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_reads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    position    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_categories_name (name),
    UNIQUE KEY uk_knowledge_categories_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO knowledge_categories (name, slug, description, position) VALUES
    ('SOP', 'sop', 'Standard operating procedures', 1),
    ('HR', 'hr', 'Policies, leave and benefits', 2),
    ('IT', 'it', 'Accounts, devices and tools', 3),
    ('Security', 'security', 'Security practices and incident response', 4),
    ('Development', 'development', 'Engineering guides and conventions', 5),
    ('Marketing', 'marketing', 'Brand, SEO and campaign playbooks', 6),
    ('Operations', 'operations', 'Office, facilities and day-to-day operations', 7),
    ('FAQ', 'faq', 'Frequently asked questions', 8),
    ('Troubleshooting', 'troubleshooting', 'Fixes for common problems', 9);

CREATE TABLE knowledge_articles (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    category_id   BIGINT       NOT NULL,
    title         VARCHAR(250) NOT NULL,
    slug          VARCHAR(270) NOT NULL,
    -- Markdown.
    body          MEDIUMTEXT   NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    author_id     BIGINT       NULL,
    department_id BIGINT       NULL,
    published_at  DATETIME(6)  NULL,
    view_count    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_articles_slug (slug),
    KEY idx_knowledge_articles_category_status (category_id, status),
    KEY idx_knowledge_articles_author (author_id),
    KEY idx_knowledge_articles_department (department_id),
    FULLTEXT KEY ft_knowledge_articles (title, body),
    CONSTRAINT fk_knowledge_articles_category FOREIGN KEY (category_id) REFERENCES knowledge_categories (id),
    CONSTRAINT fk_knowledge_articles_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_articles_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article_tags (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    KEY idx_article_tags_tag (tag_id),
    CONSTRAINT fk_article_tags_article FOREIGN KEY (article_id) REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE article_attachments (
    article_id BIGINT      NOT NULL,
    file_id    BIGINT      NOT NULL,
    added_by   BIGINT      NULL,
    added_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (article_id, file_id),
    KEY idx_article_attachments_file (file_id),
    CONSTRAINT fk_article_attachments_article FOREIGN KEY (article_id) REFERENCES knowledge_articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_attachments_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_attachments_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE documents (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    name               VARCHAR(200) NOT NULL,
    description        TEXT         NULL,
    -- NULL = company-wide.
    department_id      BIGINT       NULL,
    project_id         BIGINT       NULL,
    uploaded_by        BIGINT       NULL,
    current_version_no INT          NOT NULL DEFAULT 1,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_documents_department (department_id),
    KEY idx_documents_project (project_id),
    KEY idx_documents_uploaded_by (uploaded_by),
    CONSTRAINT fk_documents_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE document_versions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    document_id BIGINT       NOT NULL,
    version_no  INT          NOT NULL,
    file_id     BIGINT       NOT NULL,
    change_note VARCHAR(500) NULL,
    uploaded_by BIGINT       NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_versions_no (document_id, version_no),
    KEY idx_document_versions_file (file_id),
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_versions_file FOREIGN KEY (file_id) REFERENCES files (id),
    CONSTRAINT fk_document_versions_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
