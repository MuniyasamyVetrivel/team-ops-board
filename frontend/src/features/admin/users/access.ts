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

/** Each Digital Marketing edit permission and the view permission it needs (mirrors MarketingPermissionRules). */
const MARKETING_PAIRS: readonly (readonly [PermissionCode, PermissionCode])[] = [
  ['MARKETING_EDIT', 'MARKETING_VIEW'],
  ['SEO_EDIT', 'SEO_VIEW'],
  ['CAMPAIGN_EDIT', 'CAMPAIGN_VIEW'],
  ['TARGET_EDIT', 'TARGET_VIEW'],
  ['LEAD_EDIT', 'LEAD_VIEW'],
  ['BACKLINK_EDIT', 'BACKLINK_VIEW'],
  ['CONTENT_EDIT', 'CONTENT_VIEW'],
];

const MARKETING_PERMISSIONS = new Set<PermissionCode>(MARKETING_PAIRS.flat());

/**
 * Ticks or unticks a direct grant, keeping marketing permissions consistent the way the server requires: any
 * marketing permission brings "View Digital Marketing", an edit permission brings its view permission, and
 * removing a permission removes the ones that depend on it.
 */
export function toggleGrant(grants: readonly PermissionCode[], code: PermissionCode, checked: boolean): PermissionCode[] {
  const next = new Set(grants);
  if (checked) {
    next.add(code);
    if (MARKETING_PERMISSIONS.has(code)) next.add('MARKETING_VIEW');
    for (const [edit, view] of MARKETING_PAIRS) if (edit === code) next.add(view);
  } else {
    next.delete(code);
    if (code === 'MARKETING_VIEW') MARKETING_PERMISSIONS.forEach((marketing) => next.delete(marketing));
    for (const [edit, view] of MARKETING_PAIRS) if (view === code) next.delete(edit);
  }
  return [...next];
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
