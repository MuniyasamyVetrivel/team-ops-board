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
  'CELEBRATIONS_EDIT',
  'FINANCE_VIEW',
  'HISTORY_VIEW',
  'RECYCLE_BIN_MANAGE',
  'RECYCLE_BIN_PURGE',
  'BACKUP_VIEW',
  'DATA_EXPORT',
  'MODULE_MANAGE',
  'BENCH_VIEW',
  'BENCH_EDIT',
  'VENDOR_VIEW',
  'VENDOR_EDIT',
  'FINANCE_EDIT',
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

/** Money amounts (budgets, spend, approval amounts), including in audit history. */
export function canSeeFinance(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'FINANCE_VIEW');
}

/** The History tab on records: Super Admin only unless the permission was granted (the server answers 403 otherwise). */
export function canViewHistory(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'HISTORY_VIEW');
}

/** The Recycle Bin: see every deleted item and restore it (Super Admin only unless granted). */
export function canManageRecycleBin(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'RECYCLE_BIN_MANAGE');
}

/** Delete Recycle Bin items permanently: needs the bin itself as well (Super Admin only unless granted). */
export function canPurgeRecycleBin(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || (hasPermission(user, 'RECYCLE_BIN_MANAGE') && hasPermission(user, 'RECYCLE_BIN_PURGE'));
}

/** The Backups page: backup status, history and health (Super Admin only unless granted). */
export function canViewBackups(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'BACKUP_VIEW');
}

/** "Run backup now" is the Super Admin's alone (the server checks the role, not a permission). */
export function canRunBackup(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user);
}

/** "Export all data": every table as CSV plus all uploads (Super Admin only unless granted). */
export function canExportAllData(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'DATA_EXPORT');
}

/** Switch department tools on or off per department (Super Admin only unless granted). */
export function canManageModules(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user) || hasPermission(user, 'MODULE_MANAGE');
}

/** Employee IDs are set when a user is created; only a Super Admin can change them (the server enforces it). */
export function canEditEmployeeId(user: Authorities | null | undefined): boolean {
  return isSuperAdmin(user);
}

/** Everyone changes their own profile photo; only a Super Admin changes someone else's. */
export function canChangePhoto(user: (Authorities & { id: number }) | null | undefined, ownerId: number): boolean {
  return !!user && (user.id === ownerId || isSuperAdmin(user));
}

/** Highest-privilege role, for display. */
export function primaryRoleLabel(user: Authorities): string {
  const order: RoleCode[] = ['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'];
  const role = order.find((code) => user.roles.includes(code));
  return role ? ROLE_LABELS[role] : 'User';
}
