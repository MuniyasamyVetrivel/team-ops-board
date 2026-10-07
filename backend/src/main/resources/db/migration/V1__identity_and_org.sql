-- V1: identity, organisation and shared infrastructure tables.
-- Conventions: InnoDB, utf8mb4, BIGINT ids, DATETIME(6) in UTC, enums as VARCHAR(32).

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    description VARCHAR(500) NULL,
    manager_id  BIGINT       NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_name (name),
    UNIQUE KEY uk_departments_code (code),
    KEY idx_departments_manager (manager_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    email                 VARCHAR(255)  NOT NULL,
    password_hash         VARCHAR(100)  NOT NULL,
    first_name            VARCHAR(100)  NOT NULL,
    last_name             VARCHAR(100)  NOT NULL DEFAULT '',
    job_title             VARCHAR(150)  NULL,
    phone                 VARCHAR(40)   NULL,
    location              VARCHAR(150)  NULL,
    working_hours         VARCHAR(100)  NULL,
    avatar_file_id        BIGINT        NULL,
    department_id         BIGINT        NOT NULL,
    reports_to_id         BIGINT        NULL,
    weekly_capacity_hours DECIMAL(5, 2) NOT NULL DEFAULT 40.00,
    status                VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    last_login_at         DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_department (department_id),
    KEY idx_users_reports_to (reports_to_id),
    KEY idx_users_status (status),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_users_reports_to FOREIGN KEY (reports_to_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_users_capacity CHECK (weekly_capacity_hours > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_manager FOREIGN KEY (manager_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(60)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code),
    KEY idx_permissions_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission (permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Direct permission grants on top of role permissions (e.g. MARKETING_VIEW for Digital Marketing staff).
CREATE TABLE user_permissions (
    user_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    granted_by    BIGINT      NULL,
    granted_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, permission_id),
    KEY idx_user_permissions_permission (permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Secondary / cross-department memberships. The primary department is users.department_id.
CREATE TABLE department_members (
    department_id BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    member_role   VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (department_id, user_id),
    KEY idx_department_members_user (user_id),
    CONSTRAINT fk_department_members_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_department_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Only a SHA-256 hash of each refresh token is stored.
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    user_agent VARCHAR(512) NULL,
    ip_address VARCHAR(45)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id, revoked_at),
    KEY idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE app_settings (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    setting_key   VARCHAR(100)  NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    value_type    VARCHAR(20)   NOT NULL DEFAULT 'STRING',
    description   VARCHAR(255)  NULL,
    updated_by    BIGINT        NULL,
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_settings_key (setting_key),
    CONSTRAINT fk_app_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Human-readable codes (TSK-000001, TKT-000001, ...). Read with SELECT ... FOR UPDATE.
CREATE TABLE code_sequences (
    name       VARCHAR(40) NOT NULL,
    prefix     VARCHAR(10) NOT NULL,
    pad_length INT         NOT NULL,
    next_value BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Stored file metadata. The bytes live behind FileStorageService (local disk now, S3 etc. later).
CREATE TABLE files (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    storage_provider VARCHAR(32)  NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    original_name    VARCHAR(255) NOT NULL,
    content_type     VARCHAR(150) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  VARCHAR(64)  NULL,
    uploaded_by      BIGINT       NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_files_storage_key (storage_key),
    KEY idx_files_uploaded_by (uploaded_by),
    CONSTRAINT fk_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_file FOREIGN KEY (avatar_file_id) REFERENCES files (id) ON DELETE SET NULL;

CREATE TABLE tags (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(60) NOT NULL,
    color      VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60)  NULL,
    entity_id   BIGINT       NULL,
    details     JSON         NULL,
    ip_address  VARCHAR(45)  NULL,
    user_agent  VARCHAR(512) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_logs_entity (entity_type, entity_id),
    KEY idx_audit_logs_created (created_at),
    KEY idx_audit_logs_actor (actor_id),
    KEY idx_audit_logs_action (action),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
