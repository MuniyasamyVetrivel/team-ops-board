-- V28: the expansion tools are Super Admin only by default (Expansion Brief, "Access").
--
-- Each tool has its own permission so the Super Admin can later grant it to someone from Admin settings, but no
-- role except SUPER_ADMIN holds it. (The application also treats SUPER_ADMIN as holding every permission.)
-- 1. HISTORY_VIEW: the History tab on every record (X1). The Audit Logs page keeps AUDIT_VIEW.
-- 2. RECYCLE_BIN_MANAGE: the Recycle Bin page, restore and permanent delete (X2). Anyone may still delete what their
--    module lets them delete; the item goes to the bin.

INSERT INTO permissions (code, name, module, description) VALUES
    ('HISTORY_VIEW', 'View record history', 'ADMIN',
     'See the History tab on records: who changed what, and when'),
    ('RECYCLE_BIN_MANAGE', 'Manage the recycle bin', 'ADMIN',
     'Open the Recycle Bin, restore deleted items and delete them permanently');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('HISTORY_VIEW', 'RECYCLE_BIN_MANAGE');
