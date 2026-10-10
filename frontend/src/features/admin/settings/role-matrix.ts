import type { PermissionCode } from '@/features/auth/permissions';

/** Each action permission and the view permission it needs (mirrors RolePermissionRules.NEEDS_VIEW). */
export const NEEDS_VIEW: Partial<Record<PermissionCode, PermissionCode>> = {
  TASK_CREATE: 'TASK_VIEW',
  TASK_EDIT: 'TASK_VIEW',
  TASK_ASSIGN: 'TASK_VIEW',
  TASK_DELETE: 'TASK_VIEW',
  TICKET_CREATE: 'TICKET_VIEW',
  TICKET_EDIT: 'TICKET_VIEW',
  TICKET_ASSIGN: 'TICKET_VIEW',
  SLA_MANAGE: 'TICKET_VIEW',
  PROJECT_EDIT: 'PROJECT_VIEW',
  APPROVAL_DECIDE: 'APPROVAL_VIEW',
  APPROVAL_CONFIGURE: 'APPROVAL_VIEW',
  KB_EDIT: 'KB_VIEW',
  DOCUMENT_EDIT: 'DOCUMENT_VIEW',
  CALENDAR_EDIT: 'CALENDAR_VIEW',
  REPORT_EXPORT: 'REPORT_VIEW',
};

/** Digital Marketing permissions are granted to people, never to a whole role. */
export const MARKETING_MODULE = 'MARKETING';

/**
 * Ticks or unticks a permission in a role's set the way the server requires: an action brings its view
 * permission, and removing a view permission removes the actions that need it.
 */
export function toggleRolePermission(current: ReadonlySet<PermissionCode>, code: PermissionCode, checked: boolean): Set<PermissionCode> {
  const next = new Set(current);
  if (checked) {
    next.add(code);
    const view = NEEDS_VIEW[code];
    if (view) next.add(view);
  } else {
    next.delete(code);
    for (const [action, view] of Object.entries(NEEDS_VIEW) as [PermissionCode, PermissionCode][]) {
      if (view === code) next.delete(action);
    }
  }
  return next;
}

/** What saving would add and remove, sorted for display. */
export function permissionDiff(saved: readonly PermissionCode[], edited: ReadonlySet<PermissionCode>) {
  const before = new Set(saved);
  return {
    added: [...edited].filter((code) => !before.has(code)).sort(),
    removed: saved.filter((code) => !edited.has(code)).sort(),
  };
}
