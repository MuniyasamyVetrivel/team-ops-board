import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { UserDetail } from './api';
import { UserDrawer } from './UserDrawer';

const originalAdapter = api.defaults.adapter;

const usha: UserDetail = {
  id: 21,
  employeeId: 'EMP171_IN',
  photoVersion: null,
  email: 'usha.palani@teamops.local',
  firstName: 'Usha',
  lastName: 'Palani',
  fullName: 'Usha Palani',
  jobTitle: 'Talent Acquisition Specialist',
  phone: null,
  location: null,
  workingHours: null,
  timeZone: null,
  weeklyCapacityHours: 40,
  department: { id: 3, name: 'Talent Acquisition', code: 'TA' },
  reportsTo: null,
  status: 'ACTIVE',
  lastLoginAt: null,
  mustChangePassword: false,
  createdAt: '2026-10-01T00:00:00Z',
  roles: ['EMPLOYEE'],
  directPermissions: [],
  effectivePermissions: ['TASK_VIEW'],
};

/** Can manage users, but is not a Super Admin. */
const hrAdmin = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'USER_MANAGE'], { id: 6 });

function routes() {
  return {
    'GET /users/21': () => usha,
    'GET /departments': () => [],
    'GET /team': () => ({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
  };
}

describe('UserDrawer employee ID', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('is editable by the Super Admin', async () => {
    mockApi(routes());
    renderPage(<UserDrawer userId={21} onClose={() => undefined} />, superAdmin);

    const input = await screen.findByLabelText(/Employee ID/);
    expect(input).toHaveValue('EMP171_IN');
    expect(input).not.toHaveAttribute('readonly');
  });

  it('is read-only for a user administrator who is not a Super Admin', async () => {
    mockApi(routes());
    renderPage(<UserDrawer userId={21} onClose={() => undefined} />, hrAdmin);

    const input = await screen.findByLabelText(/Employee ID/);
    expect(input).toHaveValue('EMP171_IN');
    expect(input).toHaveAttribute('readonly');
    expect(screen.getByText('Only a Super Admin can change the employee ID')).toBeInTheDocument();
  });
});

describe('UserDrawer weekly capacity', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the saved weekly capacity', async () => {
    mockApi({ ...routes(), 'GET /users/21': () => ({ ...usha, weeklyCapacityHours: 37.5 }) });
    renderPage(<UserDrawer userId={21} onClose={() => undefined} />, superAdmin);

    expect(await screen.findByLabelText(/Weekly capacity/)).toHaveValue(37.5);
  });

  it('falls back to 40 hours when none is saved, so the profile can be saved without retyping it', async () => {
    mockApi({ ...routes(), 'GET /users/21': () => ({ ...usha, weeklyCapacityHours: null }) });
    renderPage(<UserDrawer userId={21} onClose={() => undefined} />, superAdmin);

    expect(await screen.findByLabelText(/Weekly capacity/)).toHaveValue(40);
  });
});
