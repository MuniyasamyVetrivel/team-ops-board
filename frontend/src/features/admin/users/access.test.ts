import { describe, expect, it } from 'vitest';

import { effectiveGrants, groupByModule, inheritedPermissions, toggleGrant } from './access';
import type { PermissionResponse, RoleResponse } from './api';

const catalogue: PermissionResponse[] = [
  { code: 'TASK_VIEW', name: 'View tasks', module: 'TASK', description: null },
  { code: 'TASK_ASSIGN', name: 'Assign tasks', module: 'TASK', description: null },
  { code: 'MARKETING_VIEW', name: 'View marketing', module: 'MARKETING', description: null },
];

const roles: RoleResponse[] = [
  { id: 1, code: 'SUPER_ADMIN', name: 'Super Admin', description: null, permissions: [] },
  { id: 2, code: 'DEPARTMENT_MANAGER', name: 'Manager', description: null, permissions: ['TASK_VIEW', 'TASK_ASSIGN'] },
  { id: 3, code: 'EMPLOYEE', name: 'Employee', description: null, permissions: ['TASK_VIEW'] },
];

describe('inheritedPermissions', () => {
  it('returns the permissions of the selected role', () => {
    expect([...inheritedPermissions(roles, ['EMPLOYEE'], catalogue)]).toEqual(['TASK_VIEW']);
  });

  it('gives Super Admin the whole catalogue regardless of role rows', () => {
    expect(inheritedPermissions(roles, ['SUPER_ADMIN'], catalogue).size).toBe(3);
  });
});

describe('effectiveGrants', () => {
  it('drops grants already covered by the role and sorts the rest', () => {
    const inherited = inheritedPermissions(roles, ['EMPLOYEE'], catalogue);
    expect(effectiveGrants(['MARKETING_VIEW', 'TASK_VIEW', 'TASK_ASSIGN'], inherited)).toEqual(['MARKETING_VIEW', 'TASK_ASSIGN']);
  });
});

describe('groupByModule', () => {
  it('keeps catalogue order within and across modules', () => {
    expect(groupByModule(catalogue).map(([module, items]) => [module, items.map((p) => p.code)])).toEqual([
      ['TASK', ['TASK_VIEW', 'TASK_ASSIGN']],
      ['MARKETING', ['MARKETING_VIEW']],
    ]);
  });
});

describe('toggleGrant', () => {
  it('adds the module and view permissions a marketing edit permission needs', () => {
    expect(toggleGrant(['TASK_VIEW'], 'SEO_EDIT', true).sort()).toEqual(['MARKETING_VIEW', 'SEO_EDIT', 'SEO_VIEW', 'TASK_VIEW']);
  });

  it('removes what depends on a permission when it is unticked', () => {
    expect(toggleGrant(['MARKETING_VIEW', 'SEO_VIEW', 'SEO_EDIT', 'LEAD_VIEW'], 'SEO_VIEW', false).sort()).toEqual(['LEAD_VIEW', 'MARKETING_VIEW']);
    expect(toggleGrant(['MARKETING_VIEW', 'SEO_VIEW', 'SEO_EDIT', 'TASK_ASSIGN'], 'MARKETING_VIEW', false)).toEqual(['TASK_ASSIGN']);
  });

  it('leaves other modules alone', () => {
    expect(toggleGrant([], 'TASK_ASSIGN', true)).toEqual(['TASK_ASSIGN']);
    expect(toggleGrant(['TASK_ASSIGN', 'TASK_VIEW'], 'TASK_VIEW', false)).toEqual(['TASK_ASSIGN']);
  });
});
