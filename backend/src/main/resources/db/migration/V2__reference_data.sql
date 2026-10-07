-- V2: reference data required in every environment (roles, permission catalogue, departments, settings).
-- Development users and sample data are NOT here; see com.teamops.devdata.DevDataSeeder.

INSERT INTO roles (code, name, description) VALUES
    ('SUPER_ADMIN',        'Super Admin',        'Full access to every department, module and setting'),
    ('DEPARTMENT_MANAGER', 'Department Manager', 'Manages the work, people and reporting of their department'),
    ('EMPLOYEE',           'Employee',           'Manages and views their own assigned work');

INSERT INTO permissions (code, name, module) VALUES
    ('DASHBOARD_VIEW',      'View dashboard',                 'DASHBOARD'),
    ('TASK_VIEW',           'View tasks',                     'TASK'),
    ('TASK_CREATE',         'Create tasks',                   'TASK'),
    ('TASK_EDIT',           'Edit tasks',                     'TASK'),
    ('TASK_ASSIGN',         'Assign and reassign tasks',      'TASK'),
    ('TASK_DELETE',         'Delete or cancel tasks',         'TASK'),
    ('WORKLOAD_VIEW',       'View workload',                  'WORKLOAD'),
    ('PROJECT_VIEW',        'View projects',                  'PROJECT'),
    ('PROJECT_EDIT',        'Create and edit projects',       'PROJECT'),
    ('TICKET_VIEW',         'View tickets',                   'TICKET'),
    ('TICKET_CREATE',       'Raise tickets',                  'TICKET'),
    ('TICKET_EDIT',         'Work on tickets',                'TICKET'),
    ('TICKET_ASSIGN',       'Assign tickets',                 'TICKET'),
    ('SLA_MANAGE',          'Configure SLA policies',         'TICKET'),
    ('APPROVAL_VIEW',       'View approvals',                 'APPROVAL'),
    ('APPROVAL_DECIDE',     'Approve or reject requests',     'APPROVAL'),
    ('APPROVAL_CONFIGURE',  'Configure approval workflows',   'APPROVAL'),
    ('ANNOUNCEMENT_MANAGE', 'Publish announcements',          'COLLABORATION'),
    ('KB_VIEW',             'View knowledge base',            'COLLABORATION'),
    ('KB_EDIT',             'Edit knowledge base',            'COLLABORATION'),
    ('DOCUMENT_VIEW',       'View documents',                 'COLLABORATION'),
    ('DOCUMENT_EDIT',       'Upload and edit documents',      'COLLABORATION'),
    ('TEAM_VIEW',           'View team directory',            'COLLABORATION'),
    ('CALENDAR_VIEW',       'View calendar',                  'CALENDAR'),
    ('CALENDAR_EDIT',       'Manage calendar events',         'CALENDAR'),
    ('REPORT_VIEW',         'View reports',                   'REPORT'),
    ('REPORT_EXPORT',       'Export reports',                 'REPORT'),
    ('USER_MANAGE',         'Create, edit and disable users', 'ADMIN'),
    ('DEPARTMENT_MANAGE',   'Create and configure departments', 'ADMIN'),
    ('SETTINGS_MANAGE',     'Change system settings',         'ADMIN'),
    ('PERMISSION_MANAGE',   'Change roles and permissions',   'ADMIN'),
    ('AUDIT_VIEW',          'View audit logs',                'ADMIN'),
    ('MARKETING_VIEW',      'View Digital Marketing',         'MARKETING'),
    ('MARKETING_EDIT',      'Edit Digital Marketing',         'MARKETING'),
    ('SEO_VIEW',            'View SEO pages and rankings',    'MARKETING'),
    ('SEO_EDIT',            'Edit SEO pages and rankings',    'MARKETING'),
    ('CAMPAIGN_VIEW',       'View campaigns',                 'MARKETING'),
    ('CAMPAIGN_EDIT',       'Edit campaigns',                 'MARKETING'),
    ('TARGET_VIEW',         'View marketing targets',         'MARKETING'),
    ('TARGET_EDIT',         'Edit marketing targets',         'MARKETING'),
    ('LEAD_VIEW',           'View leads',                     'MARKETING'),
    ('LEAD_EDIT',           'Edit leads',                     'MARKETING'),
    ('BACKLINK_VIEW',       'View backlinks',                 'MARKETING'),
    ('BACKLINK_EDIT',       'Edit backlinks',                 'MARKETING'),
    ('CONTENT_VIEW',        'View content and blogs',         'MARKETING'),
    ('CONTENT_EDIT',        'Edit content and blogs',         'MARKETING');

-- SUPER_ADMIN: every permission (the application also treats SUPER_ADMIN as all-permissions).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- DEPARTMENT_MANAGER: core modules, scoped to their department by the service layer.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'DEPARTMENT_MANAGER'
  AND p.code IN ('DASHBOARD_VIEW', 'TASK_VIEW', 'TASK_CREATE', 'TASK_EDIT', 'TASK_ASSIGN', 'TASK_DELETE',
                 'WORKLOAD_VIEW', 'PROJECT_VIEW', 'PROJECT_EDIT', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_EDIT',
                 'TICKET_ASSIGN', 'APPROVAL_VIEW', 'APPROVAL_DECIDE', 'ANNOUNCEMENT_MANAGE', 'KB_VIEW', 'KB_EDIT',
                 'DOCUMENT_VIEW', 'DOCUMENT_EDIT', 'TEAM_VIEW', 'CALENDAR_VIEW', 'CALENDAR_EDIT',
                 'REPORT_VIEW', 'REPORT_EXPORT');

-- EMPLOYEE: own work only, enforced by the service layer.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'EMPLOYEE'
  AND p.code IN ('DASHBOARD_VIEW', 'TASK_VIEW', 'TASK_CREATE', 'TASK_EDIT', 'TICKET_VIEW', 'TICKET_CREATE',
                 'PROJECT_VIEW', 'APPROVAL_VIEW', 'KB_VIEW', 'DOCUMENT_VIEW', 'TEAM_VIEW', 'CALENDAR_VIEW');

INSERT INTO departments (name, code, description) VALUES
    ('IT',                 'IT',        'IT infrastructure, support and internal systems'),
    ('Cyber Security',     'CYBERSEC',  'Security operations, compliance and risk'),
    ('HR',                 'HR',        'People operations and employee experience'),
    ('Talent Acquisition', 'TA',        'Recruitment and hiring'),
    ('Web Development',    'WEBDEV',    'Websites and web applications'),
    ('App Development',    'APPDEV',    'Mobile and desktop applications'),
    ('Digital Marketing',  'DM',        'SEO, campaigns, leads, backlinks and content'),
    ('Pre-Sales',          'PRESALES',  'Solutioning, proposals and client demos'),
    ('Graphic & Media',    'GRAPHICS',  'Design, creatives and media production'),
    ('Payroll',            'PAYROLL',   'Payroll processing and compensation');

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('workload.windowDays',               '14', 'INTEGER', 'Days ahead (plus overdue) counted towards workload %'),
    ('workload.defaultTaskHours',         '4',  'DECIMAL', 'Hours assumed for an active task without an estimate'),
    ('marketing.target.behindThresholdPct', '60', 'DECIMAL', 'Achievement % below which a target is BEHIND (red)'),
    ('sla.warningThresholdPct',           '75', 'DECIMAL', 'Elapsed SLA % at which a ticket shows a warning'),
    ('upload.maxSizeMb',                  '20', 'INTEGER', 'Maximum upload size in megabytes');

INSERT INTO code_sequences (name, prefix, pad_length, next_value) VALUES
    ('TASK',     'TSK',  6, 1),
    ('TICKET',   'TKT',  6, 1),
    ('PROJECT',  'PRJ',  4, 1),
    ('APPROVAL', 'APR',  6, 1),
    ('LEAD',     'LEAD', 6, 1);
