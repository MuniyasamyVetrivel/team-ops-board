import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import { AccessEditor } from './AccessEditor';
import type { PermissionResponse, RoleResponse, UserDetail } from './api';

const originalAdapter = api.defaults.adapter;

const roles: RoleResponse[] = [
  { id: 1, code: 'SUPER_ADMIN', name: 'Super Admin', description: 'Everything', permissions: [], version: 0 },
  { id: 2, code: 'DEPARTMENT_MANAGER', name: 'Department Manager', description: 'Department', permissions: ['TASK_VIEW', 'TASK_ASSIGN'], version: 0 },
  { id: 3, code: 'EMPLOYEE', name: 'Employee', description: 'Own work', permissions: ['TASK_VIEW'], version: 0 },
];

const catalogue: PermissionResponse[] = [
  { code: 'TASK_VIEW', name: 'View tasks', module: 'TASK', description: null },
  { code: 'TASK_ASSIGN', name: 'Assign and reassign tasks', module: 'TASK', description: null },
  { code: 'MARKETING_VIEW', name: 'View Digital Marketing', module: 'MARKETING', description: null },
];

const employee: UserDetail = {
  id: 21,
  email: 'arun.kumar@teamops.local',
  firstName: 'Arun',
  lastName: 'Kumar',
  fullName: 'Arun Kumar',
  jobTitle: 'SEO Executive',
  phone: null,
  location: null,
  workingHours: null,
  weeklyCapacityHours: 40,
  department: { id: 7, name: 'Digital Marketing', code: 'DM' },
  reportsTo: null,
  status: 'ACTIVE',
  lastLoginAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  roles: ['EMPLOYEE'],
  directPermissions: [],
  effectivePermissions: ['TASK_VIEW'],
};

describe('AccessEditor', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('locks role permissions and saves only the extra grants', async () => {
    let saved: unknown;
    mockApi({
      'GET /roles': () => roles,
      'GET /permissions': () => catalogue,
      'PUT /users/21/access': (config) => {
        saved = JSON.parse(config.data as string);
        return { ...employee, directPermissions: ['MARKETING_VIEW'] };
      },
    });

    renderPage(<AccessEditor user={employee} />, superAdmin);

    const viewTasks = await screen.findByRole('checkbox', { name: /View tasks/ });
    expect(viewTasks).toBeChecked();
    expect(viewTasks).toBeDisabled();

    const save = screen.getByRole('button', { name: 'Save access' });
    expect(save).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: /View Digital Marketing/ }));
    await userEvent.click(save);

    await screen.findByRole('checkbox', { name: /View Digital Marketing/ });
    expect(saved).toEqual({ roles: ['EMPLOYEE'], permissions: ['MARKETING_VIEW'] });
  });

  it('is read-only on your own account', async () => {
    mockApi({ 'GET /roles': () => roles, 'GET /permissions': () => catalogue });

    renderPage(<AccessEditor user={{ ...employee, id: superAdmin.id }} />, superAdmin);

    expect(await screen.findByText(/You cannot change your own access/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save access' })).not.toBeInTheDocument();
  });
});
