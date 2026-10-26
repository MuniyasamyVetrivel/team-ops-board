-- V43: The Leadership department.
--
-- A plain, active department: no department_permissions (V41) and no department tools, so the Digital Marketing,
-- celebrations and ticket rules that key off other departments are unchanged. Skipped if a Super Admin already
-- created it under this code or name.

INSERT INTO departments (name, code, description, status)
SELECT 'Leadership', 'LEADERSHIP', 'Company leadership and management team', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE code = 'LEADERSHIP' OR name = 'Leadership');
