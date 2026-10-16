-- V29: split the recycle bin permission (Expansion Brief, "Access").
--
-- RECYCLE_BIN_MANAGE now covers viewing the bin (every department) and restoring; the new RECYCLE_BIN_PURGE covers
-- deleting an item permanently. Both stay on SUPER_ADMIN only, so the Super Admin can later grant restore without
-- granting permanent delete.

UPDATE permissions
SET name = 'Manage the recycle bin',
    description = 'Open the Recycle Bin (every department) and restore deleted items'
WHERE code = 'RECYCLE_BIN_MANAGE';

INSERT INTO permissions (code, name, module, description) VALUES
    ('RECYCLE_BIN_PURGE', 'Delete permanently from the recycle bin', 'ADMIN',
     'Delete items in the Recycle Bin permanently; also needs RECYCLE_BIN_MANAGE to open the bin');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code = 'RECYCLE_BIN_PURGE';
