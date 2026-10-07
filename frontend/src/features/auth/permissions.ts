/**
 * Permission and role codes seeded by V2__reference_data.sql. The frontend uses them only to decide what to show;
 * the backend enforces every rule independently.
 */
export const PERMISSION_CODES = [
  'DASHBOARD_VIEW',
  'TASK_VIEW',
  'TASK_CREATE',
  'TASK_EDIT',
  'TASK_ASSIGN',
  'TASK_DELETE',
  'WORKLOAD_VIEW',
  'PROJECT_VIEW',
  'PROJECT_EDIT',
  'TICKET_VIEW',
  'TICKET_CREATE',
  'TICKET_EDIT',
  'TICKET_ASSIGN',
  'SLA_MANAGE',
  'APPROVAL_VIEW',
  'APPROVAL_DECIDE',
  'APPROVAL_CONFIGURE',
  'ANNOUNCEMENT_MANAGE',
  'KB_VIEW',
  'KB_EDIT',
  'DOCUMENT_VIEW',
  'DOCUMENT_EDIT',
  'TEAM_VIEW',
  'CALENDAR_VIEW',
  'CALENDAR_EDIT',
  'REPORT_VIEW',
  'REPORT_EXPORT',
  'USER_MANAGE',
  'DEPARTMENT_MANAGE',
  'SETTINGS_MANAGE',
  'PERMISSION_MANAGE',
  'AUDIT_VIEW',
  'MARKETING_VIEW',
  'MARKETING_EDIT',
  'SEO_VIEW',
  'SEO_EDIT',
  'CAMPAIGN_VIEW',
  'CAMPAIGN_EDIT',
  'TARGET_VIEW',
  'TARGET_EDIT',
  'LEAD_VIEW',
  'LEAD_EDIT',
  'BACKLINK_VIEW',
  'BACKLINK_EDIT',
  'CONTENT_VIEW',
  'CONTENT_EDIT',
] as const;

export type PermissionCode = (typeof PERMISSION_CODES)[number];

export type RoleCode = 'SUPER_ADMIN' | 'DEPARTMENT_MANAGER' | 'EMPLOYEE';

export const ROLE_LABELS: Record<RoleCode, string> = {
  SUPER_ADMIN: 'Super Admin',
  DEPARTMENT_MANAGER: 'Department Manager',
  EMPLOYEE: 'Employee',
};

interface Authorities {
  roles: readonly RoleCode[];
  permissions: readonly PermissionCode[];
}

export function hasPermission(user: Authorities | null | undefined, permission: PermissionCode): boolean {
  return user?.permissions.includes(permission) ?? false;
}

export function isSuperAdmin(user: Authorities | null | undefined): boolean {
  return user?.roles.includes('SUPER_ADMIN') ?? false;
}

/** Highest-privilege role, for display. */
export function primaryRoleLabel(user: Authorities): string {
  const order: RoleCode[] = ['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'];
  const role = order.find((code) => user.roles.includes(code));
  return role ? ROLE_LABELS[role] : 'User';
}
