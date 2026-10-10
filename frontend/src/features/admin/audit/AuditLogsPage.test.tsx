import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { AuditCatalog, AuditLogItem } from './api';
import AuditLogsPage from './AuditLogsPage';

const originalAdapter = api.defaults.adapter;

const priya = { id: 7, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'SEO Lead', status: 'ACTIVE' as const };

const catalog: AuditCatalog = {
  modules: [
    { key: 'SIGN_IN', label: 'Sign-in' },
    { key: 'TASKS', label: 'Tasks' },
    { key: 'USERS', label: 'Users and access' },
  ],
  actions: [
    { action: 'LOGIN', label: 'Signed in', module: 'SIGN_IN', events: ['USER_LOGIN'] },
    { action: 'TASK_STATUS_CHANGED', label: 'Task status changed', module: 'TASKS', events: ['TASK_STATUS_CHANGE'] },
    { action: 'ROLE_PERMISSIONS_CHANGED', label: 'Role permissions changed', module: 'USERS', events: ['USER_PERMISSION_CHANGE'] },
  ],
  events: [
    { key: 'USER_LOGIN', label: 'User login', actions: ['LOGIN'] },
    { key: 'TASK_STATUS_CHANGE', label: 'Task status change', actions: ['TASK_STATUS_CHANGED'] },
    { key: 'USER_PERMISSION_CHANGE', label: 'User permission change', actions: ['ROLE_PERMISSIONS_CHANGED', 'USER_ACCESS_CHANGED'] },
  ],
  entityTypes: ['ROLE', 'TASK', 'USER'],
  actors: [priya],
};

const statusChange: AuditLogItem = {
  id: 41,
  action: 'TASK_STATUS_CHANGED',
  actionLabel: 'Task status changed',
  module: 'TASKS',
  moduleLabel: 'Tasks',
  actor: priya,
  entityType: 'TASK',
  entityId: 128,
  details: { changes: { status: { from: 'IN_PROGRESS', to: 'COMPLETED' } } },
  ipAddress: '10.0.0.5',
  userAgent: 'Mozilla/5.0',
  createdAt: '2026-10-09T06:30:00Z',
};

const roleChange: AuditLogItem = {
  ...statusChange,
  id: 42,
  action: 'ROLE_PERMISSIONS_CHANGED',
  actionLabel: 'Role permissions changed',
  module: 'USERS',
  moduleLabel: 'Users and access',
  entityType: 'ROLE',
  entityId: 3,
  details: { role: 'EMPLOYEE', added: ['WORKLOAD_VIEW'], removed: [] },
};

const page = (content: AuditLogItem[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: content.length ? 1 : 0 });

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /admin/audit-logs/catalog': () => catalog,
    'GET /admin/audit-logs': () => page([statusChange, roleChange]),
    ...extra,
  };
}

describe('AuditLogsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  it('lists entries with a one-line change and opens the before/after values', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<AuditLogsPage />, superAdmin);

    const table = await screen.findByRole('table', { name: 'Audit log entries' });
    const row = within(table).getByRole('row', { name: 'Task status changed by Priya Menon' });
    expect(row).toHaveTextContent('TASK #128');
    expect(row).toHaveTextContent('Status: IN_PROGRESS → COMPLETED');
    expect(within(table).getByRole('row', { name: 'Role permissions changed by Priya Menon' })).toHaveTextContent('Added WORKLOAD_VIEW');
    expect(screen.getByText('2 entries')).toBeInTheDocument();

    await user.click(row);
    const sheet = await screen.findByRole('dialog');
    const changes = within(sheet).getByRole('table', { name: 'Before and after values' });
    expect(within(changes).getByRole('row', { name: /Status/ })).toHaveTextContent('IN_PROGRESSCOMPLETED');
    expect(sheet).toHaveTextContent('IP address: 10.0.0.5');
  });

  it('filters by the brief’s audit events, people, modules, actions and dates', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /admin/audit-logs': (config) => (requests.push(config), page([statusChange])) }));
    renderPage(<AuditLogsPage />, superAdmin);

    await screen.findByRole('table', { name: 'Audit log entries' });
    expect(requests[0]?.params).toEqual({ sort: 'created,desc', page: 0, size: 50 });

    await user.selectOptions(screen.getByLabelText('Audit event'), 'USER_PERMISSION_CHANGE');
    await user.selectOptions(screen.getByLabelText('Person'), '7');
    await user.selectOptions(screen.getByLabelText('Module'), 'TASKS');
    // The action list follows the module.
    expect(within(screen.getByLabelText('Action')).getAllByRole('option').map((o) => o.textContent)).toEqual(['All actions', 'Task status changed']);
    await user.selectOptions(screen.getByLabelText('Action'), 'TASK_STATUS_CHANGED');
    await user.type(screen.getByLabelText('From'), '2026-10-01');
    expect(requests.at(-1)?.params).toEqual({
      event: 'USER_PERMISSION_CHANGE',
      actorId: 7,
      module: 'TASKS',
      action: ['TASK_STATUS_CHANGED'],
      from: '2026-10-01',
      sort: 'created,desc',
      page: 0,
      size: 50,
    });

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(requests.at(-1)?.params).toEqual({ sort: 'created,desc', page: 0, size: 50 });
  }, 15_000);

  it('exports the filtered entries as CSV', async () => {
    const user = userEvent.setup();
    const exports: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /admin/audit-logs/export?event=USER_LOGIN&sort=created%2Cdesc&format=CSV': (config) => (exports.push(config), new Blob(['csv'])) }));
    URL.createObjectURL = vi.fn(() => 'blob:audit');
    URL.revokeObjectURL = vi.fn();
    renderPage(<AuditLogsPage />, superAdmin);

    await screen.findByRole('table', { name: 'Audit log entries' });
    await user.selectOptions(screen.getByLabelText('Audit event'), 'USER_LOGIN');
    await user.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(exports).toHaveLength(1);
  });

  it('says when nothing matches', async () => {
    mockApi(routes({ 'GET /admin/audit-logs': () => page([]) }));
    renderPage(<AuditLogsPage />, superAdmin);

    expect(await screen.findByText('Nothing has been audited yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeDisabled();
  });
});
