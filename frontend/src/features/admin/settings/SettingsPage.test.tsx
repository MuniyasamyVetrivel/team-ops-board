import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import type { PermissionResponse, RoleResponse } from '@/features/admin/users/api';
import type { ApprovalType } from '@/features/approvals/api';
import { api } from '@/lib/api/client';
import { makeUser, superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { SettingItem } from './api';
import SettingsPage from './SettingsPage';

const originalAdapter = api.defaults.adapter;

const rakesh = { id: 1, fullName: 'Rakesh', email: 'rakesh@teamops.local', jobTitle: 'Director', status: 'ACTIVE' as const };

const settings: SettingItem[] = [
  { key: 'workload.windowDays', group: 'Workload', label: 'Workload window', description: 'Days ahead', valueType: 'INTEGER', value: '14', unit: 'days', min: 1, max: 90, updatedBy: rakesh, updatedAt: '2026-10-01T05:00:00Z', version: 2 },
  { key: 'workload.defaultTaskHours', group: 'Workload', label: 'Hours for a task without an estimate', description: null, valueType: 'DECIMAL', value: '4', unit: 'hours', min: 0, max: 40, updatedBy: null, updatedAt: '2026-09-01T05:00:00Z', version: 0 },
  { key: 'sla.warningThresholdPct', group: 'Help desk', label: 'SLA warning threshold', description: null, valueType: 'INTEGER', value: '75', unit: '%', min: 1, max: 99, updatedBy: null, updatedAt: '2026-09-01T05:00:00Z', version: 0 },
];

const permissions: PermissionResponse[] = [
  { code: 'TASK_VIEW', name: 'View tasks', module: 'TASK', description: null },
  { code: 'TASK_EDIT', name: 'Edit tasks', module: 'TASK', description: null },
  { code: 'WORKLOAD_VIEW', name: 'View workload', module: 'WORKLOAD', description: null },
  { code: 'LEAD_VIEW', name: 'View leads', module: 'MARKETING', description: null },
];

const roles: RoleResponse[] = [
  { id: 1, code: 'SUPER_ADMIN', name: 'Super Admin', description: null, permissions: ['TASK_VIEW', 'TASK_EDIT', 'WORKLOAD_VIEW', 'LEAD_VIEW'], version: 0 },
  { id: 2, code: 'DEPARTMENT_MANAGER', name: 'Department Manager', description: null, permissions: ['TASK_VIEW', 'TASK_EDIT', 'WORKLOAD_VIEW'], version: 3 },
  { id: 3, code: 'EMPLOYEE', name: 'Employee', description: null, permissions: ['TASK_VIEW', 'TASK_EDIT'], version: 5 },
];

const purchase: ApprovalType = {
  id: 3,
  code: 'PURCHASE',
  name: 'Purchase',
  description: 'Equipment',
  requiresAmount: true,
  active: true,
  steps: [{ stepOrder: 1, approverKind: 'DEPARTMENT_MANAGER', role: null, user: null }],
  version: 1,
};

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /admin/settings': () => settings,
    'GET /roles': () => roles,
    'GET /permissions': () => permissions,
    'GET /approvals/types': () => [purchase],
    'GET /team': () => ({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
    ...extra,
  };
}

function conflict(code: string, message: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError(message, '409', config, null, {
    status: 409,
    statusText: '',
    headers: {},
    config,
    data: { timestamp: '', status: 409, error: '', code, message, path: '' },
  });
}

const body = (config: InternalAxiosRequestConfig) => JSON.parse(config.data as string) as Record<string, unknown>;

describe('SettingsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('edits a setting within its range and saves it with its version', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'PUT /admin/settings/workload.windowDays': (config) => {
          puts.push(config);
          return { ...settings[0], value: '21', version: 3 };
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin);

    const input = await screen.findByLabelText('Workload window');
    expect(input).toHaveValue('14');
    expect(screen.getByText(/Last changed by Rakesh/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save Workload window' })).toBeDisabled();

    await user.clear(input);
    await user.type(input, '120');
    await user.click(screen.getByRole('button', { name: 'Save Workload window' }));
    expect(await screen.findByText('Workload window must be between 1 and 90 days')).toBeInTheDocument();
    expect(puts).toHaveLength(0);

    await user.clear(input);
    await user.type(input, '21');
    await user.click(screen.getByRole('button', { name: 'Save Workload window' }));
    expect(puts).toHaveLength(1);
    expect(body(puts[0]!)).toEqual({ version: 2, value: '21' });
    expect(await screen.findByLabelText('Workload window')).toHaveValue('21');
  }, 15_000);

  it('explains a conflicting edit', async () => {
    const user = userEvent.setup();
    mockApi(
      routes({
        'PUT /admin/settings/sla.warningThresholdPct': () => {
          throw conflict('STALE_UPDATE', 'Someone else changed this setting just now. Reload and try again.');
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin);

    const input = await screen.findByLabelText('SLA warning threshold');
    await user.clear(input);
    await user.type(input, '80');
    await user.click(screen.getByRole('button', { name: 'Save SLA warning threshold' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this setting just now');
  });

  it('edits a role in the permission matrix and confirms before saving', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'PUT /roles/EMPLOYEE/permissions': (config) => {
          puts.push(config);
          return { ...roles[2], permissions: ['TASK_VIEW', 'TASK_EDIT', 'WORKLOAD_VIEW'], version: 6 };
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=permissions');

    const matrix = await screen.findByRole('table', { name: 'Role permissions' });
    // The Super Admin role is locked; marketing permissions are per person.
    expect(within(matrix).queryByRole('checkbox', { name: 'Super Admin: View tasks' })).not.toBeInTheDocument();
    expect(within(matrix).getAllByText('Per person').length).toBe(2);

    await user.click(within(matrix).getByRole('checkbox', { name: 'Employee: View workload' }));
    // Removing "View tasks" also removes "Edit tasks", which needs it; ticking it back does not restore the edit.
    await user.click(within(matrix).getByRole('checkbox', { name: 'Employee: View tasks' }));
    expect(within(matrix).getByRole('checkbox', { name: 'Employee: Edit tasks' })).not.toBeChecked();
    await user.click(within(matrix).getByRole('checkbox', { name: 'Employee: Edit tasks' }));
    expect(within(matrix).getByRole('checkbox', { name: 'Employee: View tasks' })).toBeChecked();

    expect(screen.getByRole('status', { name: 'Employee unsaved changes' })).toHaveTextContent('+1 added');
    await user.click(screen.getByRole('button', { name: 'Review and save' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('list', { name: 'Added permissions' })).toHaveTextContent('View workload');
    await user.click(within(dialog).getByRole('button', { name: 'Save permissions' }));

    expect(puts).toHaveLength(1);
    expect(body(puts[0]!)).toEqual({ version: 5, permissions: ['TASK_EDIT', 'TASK_VIEW', 'WORKLOAD_VIEW'] });
  }, 15_000);

  it('shows the matrix read-only to access admins who are not Super Admins', async () => {
    mockApi(routes());
    renderPage(<SettingsPage />, makeUser(['DEPARTMENT_MANAGER'], ['SETTINGS_MANAGE', 'PERMISSION_MANAGE']), '/admin/settings?tab=permissions');

    const matrix = await screen.findByRole('table', { name: 'Role permissions' });
    expect(within(matrix).getByRole('checkbox', { name: 'Employee: View tasks' })).toBeDisabled();
    expect(screen.getByText('Only a Super Admin can change role permissions.')).toBeInTheDocument();
    // No workflow tab without APPROVAL_CONFIGURE.
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['General', 'Roles & permissions']);
  });

  it('adds an approval type with its workflow', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'POST /approvals/types': (config) => {
          posts.push(config);
          return { ...purchase, id: 9, code: 'TRAVEL_REQUEST', name: 'Travel request', requiresAmount: false };
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=workflows');

    expect(await screen.findByRole('button', { name: 'Edit Purchase workflow' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'New approval type' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Name/), 'Travel request');
    expect(within(dialog).getByLabelText(/Code/)).toHaveValue('TRAVEL_REQUEST');
    await user.click(within(dialog).getByRole('button', { name: 'Add approval type' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({
      code: 'TRAVEL_REQUEST',
      name: 'Travel request',
      description: null,
      requiresAmount: false,
      steps: [{ approverKind: 'DEPARTMENT_MANAGER', roleCode: null, userId: null }],
    });
  }, 15_000);

  it('retires an approval type from its details', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'PUT /approvals/types/3': (config) => {
          puts.push(config);
          return { ...purchase, active: false, version: 2 };
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=workflows');

    await user.click(await screen.findByRole('button', { name: 'Edit Purchase details' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('checkbox', { name: /Active/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(body(puts[0]!)).toEqual({ version: 1, name: 'Purchase', description: 'Equipment', requiresAmount: true, active: false });
  }, 15_000);
});
