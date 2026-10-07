import { describe, expect, it } from 'vitest';

import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';

import { hasPermission, isSuperAdmin, primaryRoleLabel } from './permissions';

describe('permissions', () => {
  it('checks permissions from the server-provided list', () => {
    expect(hasPermission(webEmployee, 'TASK_VIEW')).toBe(true);
    expect(hasPermission(webEmployee, 'USER_MANAGE')).toBe(false);
    expect(hasPermission(null, 'TASK_VIEW')).toBe(false);
  });

  it('recognises the Super Admin', () => {
    expect(isSuperAdmin(superAdmin)).toBe(true);
    expect(isSuperAdmin(webEmployee)).toBe(false);
  });

  it('labels a user by their highest role', () => {
    expect(primaryRoleLabel(superAdmin)).toBe('Super Admin');
    expect(primaryRoleLabel(makeUser(['EMPLOYEE', 'DEPARTMENT_MANAGER'], []))).toBe('Department Manager');
    expect(primaryRoleLabel(webEmployee)).toBe('Employee');
  });
});
