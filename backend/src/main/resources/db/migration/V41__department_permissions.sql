-- V41: Permissions that come with a department.
--
-- Every active user whose primary department (users.department_id) holds a row here gets that permission, on top of
-- their roles and direct grants. Moving a user out of the department takes it away again; nothing is granted per user.
-- The Digital Marketing department (code DM) gets the whole Digital Marketing area, celebrations included.

CREATE TABLE department_permissions (
    department_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (department_id, permission_id),
    KEY idx_department_permissions_permission (permission_id),
    CONSTRAINT fk_department_permissions_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_department_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO department_permissions (department_id, permission_id)
SELECT d.id, p.id
FROM departments d
JOIN permissions p ON p.code IN ('MARKETING_VIEW', 'MARKETING_EDIT', 'SEO_VIEW', 'SEO_EDIT', 'CAMPAIGN_VIEW',
                                 'CAMPAIGN_EDIT', 'TARGET_VIEW', 'TARGET_EDIT', 'LEAD_VIEW', 'LEAD_EDIT',
                                 'BACKLINK_VIEW', 'BACKLINK_EDIT', 'CONTENT_VIEW', 'CONTENT_EDIT', 'CELEBRATIONS_EDIT')
WHERE d.code = 'DM';
