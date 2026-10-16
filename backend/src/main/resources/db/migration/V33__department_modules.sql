-- V33: department modules framework (Expansion Phase X5).
--
-- 1. department_modules: which department tools (Bench Management, Vendors, Finance, ...) are switched on for which
--    department. The modules themselves are defined in code (a ModuleDefinition bean each: key, name, icon and their
--    own VIEW and EDIT permissions); this table only holds the per-department switch. A row is kept when a module is
--    switched off again (enabled = FALSE), so its history stays on one record. A module is "owned" by the departments it
--    is switched on for; each module's own migration seeds its default departments.
-- 2. MODULE_MANAGE: switch modules on or off per department (Admin settings). Super Admin only by default,
--    grantable later from Admin settings.

CREATE TABLE department_modules (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    module_key    VARCHAR(40) NOT NULL,
    department_id BIGINT      NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by    BIGINT      NULL,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_modules_module_department (module_key, department_id),
    KEY idx_department_modules_department (department_id, enabled),
    CONSTRAINT fk_department_modules_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, name, module, description) VALUES
    ('MODULE_MANAGE', 'Manage department tools', 'ADMIN',
     'Switch department tools (modules such as Bench, Vendors and Finance) on or off per department');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code = 'MODULE_MANAGE';
