import type { PermissionCode, RoleCode } from '@/features/auth/permissions';

import type { PermissionResponse, RoleResponse } from './api';

/** Permissions a user gets from the selected roles. SUPER_ADMIN implies every permission. */
export function inheritedPermissions(
  roles: readonly RoleResponse[],
  selected: readonly RoleCode[],
  catalogue: readonly PermissionResponse[],
): Set<PermissionCode> {
  if (selected.includes('SUPER_ADMIN')) return new Set(catalogue.map((p) => p.code));
  return new Set(roles.filter((role) => selected.includes(role.code)).flatMap((role) => role.permissions));
}

/** Groups the catalogue by module, preserving server order. */
export function groupByModule(catalogue: readonly PermissionResponse[]): [string, PermissionResponse[]][] {
  const groups = new Map<string, PermissionResponse[]>();
  for (const permission of catalogue) {
    const list = groups.get(permission.module) ?? [];
    list.push(permission);
    groups.set(permission.module, list);
  }
  return [...groups.entries()];
}

/**
 * Direct grants worth sending: those not already covered by the roles. Keeps the stored grants minimal, so
 * removing a role later does not leave surprising leftovers.
 */
export function effectiveGrants(grants: readonly PermissionCode[], inherited: ReadonlySet<PermissionCode>): PermissionCode[] {
  return grants.filter((code) => !inherited.has(code)).sort();
}

export const MODULE_LABELS: Record<string, string> = {
  DASHBOARD: 'Dashboard',
  TASK: 'Tasks',
  WORKLOAD: 'Workload',
  PROJECT: 'Projects',
  TICKET: 'Help desk',
  APPROVAL: 'Approvals',
  COLLABORATION: 'Collaboration',
  CALENDAR: 'Calendar',
  REPORT: 'Reports',
  ADMIN: 'Administration',
  MARKETING: 'Digital Marketing',
};
