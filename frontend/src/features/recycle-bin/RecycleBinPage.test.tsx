import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { RecycleBinCatalog, RecycleBinItem, RestoreCheck } from './api';
import RecycleBinPage from './RecycleBinPage';

const originalAdapter = api.defaults.adapter;

const sanjay = { id: 3, fullName: 'Sanjay Varma', email: 'sanjay.varma@teamops.local', jobTitle: 'Manager', status: 'ACTIVE' as const };

const catalog: RecycleBinCatalog = {
  modules: [
    { key: 'CELEBRATIONS', label: 'Celebrations' },
    { key: 'TASKS', label: 'Tasks' },
  ],
  types: [
    { type: 'EMPLOYEE', label: 'Employee', module: 'CELEBRATIONS' },
    { type: 'TASK', label: 'Task', module: 'TASKS' },
  ],
  deletedBy: [sanjay],
  retentionDays: 90,
};

const task: RecycleBinItem = {
  id: 11,
  entityType: 'TASK',
  entityTypeLabel: 'Task',
  entityId: 42,
  label: 'TSK-000042',
  module: 'TASKS',
  moduleLabel: 'Tasks',
  department: { id: 5, name: 'Web' },
  deletedAt: '2026-10-14T06:30:00Z',
  deletedBy: sanjay,
  purgeOn: '2027-01-12',
  daysUntilPurge: 88,
  confirmationText: 'TSK-000042',
};

const employee: RecycleBinItem = {
  ...task,
  id: 12,
  entityType: 'EMPLOYEE',
  entityTypeLabel: 'Employee',
  entityId: 7,
  label: 'Priya Menon',
  module: 'CELEBRATIONS',
  moduleLabel: 'Celebrations',
  purgeOn: '2026-10-17',
  daysUntilPurge: 1,
  confirmationText: 'Priya Menon',
};

const page = (content: RecycleBinItem[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const clear: RestoreCheck = { canRestore: true, blockCode: null, blockMessage: null, warnings: [] };

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /recycle-bin/catalog': () => catalog,
    'GET /recycle-bin': () => page([task, employee]),
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

/** Restore only: RECYCLE_BIN_MANAGE without RECYCLE_BIN_PURGE. */
const keeper = makeUser(['EMPLOYEE'], ['DASHBOARD_VIEW', 'RECYCLE_BIN_MANAGE'], { id: 13, fullName: 'Bin Keeper' });

describe('RecycleBinPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists deleted items with module, who deleted them, when, and days until the automatic delete', async () => {
    mockApi(routes());
    renderPage(<RecycleBinPage />, superAdmin);

    const table = await screen.findByRole('table', { name: 'Deleted items' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByText('TSK-000042')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Task · Web')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Tasks')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Sanjay Varma')).toBeInTheDocument();
    expect(within(rows[1]).getByText('In 88 days')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Tomorrow')).toBeInTheDocument();
    expect(screen.getByText(/deleted permanently after 90 days/)).toBeInTheDocument();
  });

  it('sends the module, deleted by, date range and search filters', async () => {
    const adapter = mockApi(routes());
    renderPage(<RecycleBinPage />, superAdmin);
    await screen.findByRole('table', { name: 'Deleted items' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Module' }), 'TASKS');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Deleted by' }), '3');
    await userEvent.type(screen.getByLabelText('Deleted from'), '2026-10-01');
    await userEvent.type(screen.getByLabelText('To'), '2026-10-15');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search the recycle bin' }), 'TSK');

    await waitFor(() => {
      const params = adapter.mock.calls.map(([config]) => config).filter((c) => c.url === '/recycle-bin').at(-1)?.params as Record<string, unknown>;
      expect(params).toMatchObject({ module: 'TASKS', deletedById: 3, from: '2026-10-01', to: '2026-10-15', search: 'TSK' });
    });
  });

  it('shows the empty state', async () => {
    mockApi(routes({ 'GET /recycle-bin': () => page([]) }));
    renderPage(<RecycleBinPage />, superAdmin);
    expect(await screen.findByText('The recycle bin is empty')).toBeInTheDocument();
  });

  it('warns about a reused work email before restoring, and restores anyway', async () => {
    let restored = false;
    mockApi(
      routes({
        'GET /recycle-bin/12/restore-check': () => ({ ...clear, warnings: ['The work email priya@teamops.local now belongs to employee E-9 (Priya M).'] }),
        'POST /recycle-bin/12/restore': () => {
          restored = true;
          return { entityType: 'EMPLOYEE', entityId: 7, label: 'Priya Menon' };
        },
      }),
    );
    renderPage(<RecycleBinPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'Restore Priya Menon' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText(/now belongs to employee E-9/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Restore anyway' }));
    await waitFor(() => expect(restored).toBe(true));
  });

  it('blocks the restore when another employee now has the employee ID', async () => {
    mockApi(
      routes({
        'GET /recycle-bin/12/restore-check': () => ({ canRestore: false, blockCode: 'RESTORE_CONFLICT', blockMessage: 'Another employee now has the employee ID E-7.', warnings: [] }),
      }),
    );
    renderPage(<RecycleBinPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'Restore Priya Menon' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Another employee now has the employee ID E-7.');
    expect(within(dialog).getByRole('button', { name: 'Restore' })).toBeDisabled();
  });

  it('shows a clash the server finds while restoring', async () => {
    mockApi(
      routes({
        'GET /recycle-bin/11/restore-check': () => clear,
        'POST /recycle-bin/11/restore': () => {
          throw conflict('RESTORE_CONFLICT', 'Another record now uses one of its unique values.');
        },
      }),
    );
    renderPage(<RecycleBinPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'Restore TSK-000042' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Restore' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Another record now uses one of its unique values.');
  });

  it('deletes permanently only after the item is named', async () => {
    let confirm: unknown = null;
    mockApi(
      routes({
        'POST /recycle-bin/11/purge': (config) => {
          confirm = (JSON.parse(config.data as string) as { confirm: string }).confirm;
          return '';
        },
      }),
    );
    renderPage(<RecycleBinPage />, superAdmin);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete TSK-000042 permanently' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Delete “TSK-000042” permanently?' })).toBeInTheDocument();
    const submit = within(dialog).getByRole('button', { name: 'Delete permanently' });
    expect(submit).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText(/to confirm/), 'TSK-000041');
    expect(submit).toBeDisabled();
    await userEvent.clear(within(dialog).getByLabelText(/to confirm/));
    await userEvent.type(within(dialog).getByLabelText(/to confirm/), 'tsk-000042 ');
    await userEvent.click(submit);
    await waitFor(() => expect(confirm).toBe('tsk-000042 '));
  });

  it('hides permanent delete without RECYCLE_BIN_PURGE', async () => {
    mockApi(routes());
    renderPage(<RecycleBinPage />, keeper);

    expect(await screen.findByRole('button', { name: 'Restore TSK-000042' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /permanently/ })).not.toBeInTheDocument();
  });
});
