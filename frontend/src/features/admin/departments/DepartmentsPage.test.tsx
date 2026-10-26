import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { DepartmentDetail, DepartmentListItem } from '@/features/departments/api';
import type { TeamMember } from '@/features/team/api';
import { api } from '@/lib/api/client';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import { DepartmentFormDialog } from './DepartmentFormDialog';
import DepartmentsPage from './DepartmentsPage';

const originalAdapter = api.defaults.adapter;

const leadership: DepartmentListItem = {
  id: 11,
  name: 'Leadership',
  code: 'LEADERSHIP',
  description: 'Company leadership and management team',
  status: 'ACTIVE',
  manager: null,
  memberCount: 0,
  secondaryMemberCount: 0,
};

const payroll: DepartmentListItem = { ...leadership, id: 10, name: 'Payroll', code: 'PAYROLL', description: null, status: 'INACTIVE' };

const rakesh: TeamMember = {
  id: 1,
  fullName: 'Rakesh Kumar',
  firstName: 'Rakesh',
  lastName: 'Kumar',
  email: 'rakesh@teamops.local',
  jobTitle: 'Director',
  department: { id: 11, name: 'Leadership', code: 'LEADERSHIP' },
  manager: null,
  phone: null,
  location: null,
  workingHours: null,
  status: 'ACTIVE',
  employeeId: 'EMP001',
  photoVersion: null,
};

function detail(overrides: Partial<DepartmentDetail>): DepartmentDetail {
  return { ...leadership, createdAt: '2026-10-26T00:00:00Z', members: [], canEdit: true, canManageMembers: true, ...overrides };
}

const directory = () => ({ content: [rakesh], page: 0, size: 100, totalElements: 1, totalPages: 1 });
const emptyHistory = () => ({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

describe('DepartmentsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists departments with their status labels', async () => {
    mockApi({ 'GET /departments': () => [leadership, payroll] });

    renderPage(<DepartmentsPage />, superAdmin);

    const row = await screen.findByRole('row', { name: 'Open Leadership' });
    expect(within(row).getByText('LEADERSHIP')).toBeInTheDocument();
    expect(within(row).getByText('Active')).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: 'Open Payroll' })).getByText('Inactive')).toBeInTheDocument();
  });

  it('creates a department with name, code, description, manager and status', async () => {
    const created = detail({ id: 12, name: 'Legal', code: 'LEGAL', description: 'Contracts', status: 'INACTIVE' });
    const adapter = mockApi({
      'GET /departments': () => [leadership],
      'GET /team': directory,
      'POST /departments': () => created,
      'GET /departments/12': () => created,
      'GET /history/DEPARTMENT/12': emptyHistory,
    });

    renderPage(<DepartmentsPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'New department' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Name/), 'Legal');
    await userEvent.type(within(dialog).getByLabelText(/^Code/), 'legal');
    await userEvent.type(within(dialog).getByLabelText('Description'), 'Contracts');
    await within(dialog).findByRole('option', { name: 'Rakesh Kumar — Leadership' });
    await userEvent.selectOptions(within(dialog).getByLabelText('Department manager'), '1');
    await userEvent.selectOptions(within(dialog).getByLabelText('Status'), 'INACTIVE');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create department' }));

    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.method === 'post')).toBe(true));
    const post = adapter.mock.calls.find(([config]) => config.method === 'post');
    expect(JSON.parse(String(post?.[0].data))).toEqual({
      name: 'Legal',
      code: 'legal',
      description: 'Contracts',
      managerId: 1,
      status: 'INACTIVE',
    });
  });

  it('checks the code format before sending', async () => {
    const adapter = mockApi({ 'GET /departments': () => [], 'GET /team': directory });

    renderPage(<DepartmentsPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'New department' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Name/), 'Quality');
    await userEvent.type(within(dialog).getByLabelText(/^Code/), 'has spaces');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create department' }));

    expect(await within(dialog).findByText('2–40 letters, digits or underscores')).toBeInTheDocument();
    expect(adapter.mock.calls.some(([config]) => config.method === 'post')).toBe(false);
  });
});

describe('DepartmentFormDialog', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('deactivates a department without sending the code', async () => {
    const current = detail({ manager: null });
    const adapter = mockApi({
      'GET /team': directory,
      'PUT /departments/11': () => ({ ...current, status: 'INACTIVE' }),
    });

    renderPage(<DepartmentFormDialog open onOpenChange={() => undefined} department={current} />, superAdmin);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText(/^Code/)).toBeDisabled();
    await userEvent.selectOptions(within(dialog).getByLabelText('Status'), 'INACTIVE');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.method === 'put')).toBe(true));
    const put = adapter.mock.calls.find(([config]) => config.method === 'put');
    expect(JSON.parse(String(put?.[0].data))).toEqual({
      name: 'Leadership',
      description: 'Company leadership and management team',
      managerId: null,
      status: 'INACTIVE',
    });
  });
});
