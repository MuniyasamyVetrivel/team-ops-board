import type { PermissionCode, RoleCode } from '@/features/auth/permissions';
import { PERMISSION_CODES } from '@/features/auth/permissions';
import type { CurrentUser } from '@/features/auth/types';

export function makeUser(roles: RoleCode[], permissions: PermissionCode[], overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: 1,
    email: 'user@teamops.local',
    firstName: 'Test',
    lastName: 'User',
    fullName: 'Test User',
    jobTitle: null,
    department: { id: 1, name: 'IT', code: 'IT' },
    roles,
    permissions,
    ...overrides,
  };
}

/** Mirrors the backend: SUPER_ADMIN receives every permission. */
export const superAdmin = makeUser(['SUPER_ADMIN'], [...PERMISSION_CODES], { fullName: 'Rakesh', firstName: 'Rakesh', lastName: '' });

export const webEmployee = makeUser(
  ['EMPLOYEE'],
  ['DASHBOARD_VIEW', 'TASK_VIEW', 'TASK_CREATE', 'TASK_EDIT', 'TICKET_VIEW', 'TICKET_CREATE', 'PROJECT_VIEW', 'APPROVAL_VIEW', 'KB_VIEW', 'DOCUMENT_VIEW', 'TEAM_VIEW', 'CALENDAR_VIEW'],
);

export const seoExecutive = makeUser(['EMPLOYEE'], [
  ...webEmployee.permissions,
  'MARKETING_VIEW',
  'SEO_VIEW',
  'SEO_EDIT',
  'TARGET_VIEW',
  'BACKLINK_VIEW',
  'BACKLINK_EDIT',
  'CONTENT_VIEW',
]);
