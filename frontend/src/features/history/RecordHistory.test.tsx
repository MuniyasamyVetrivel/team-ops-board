import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import type { AuditLogItem } from '@/features/admin/audit/api';
import { api } from '@/lib/api/client';
import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import { RecordHistory } from './RecordHistory';

const originalAdapter = api.defaults.adapter;

const priya = { id: 7, fullName: 'Priya Menon', email: 'priya@teamops.local', jobTitle: null, status: 'ACTIVE' as const };

function entry(overrides: Partial<AuditLogItem>): AuditLogItem {
  return {
    id: 1,
    action: 'RECORD_UPDATED',
    actionLabel: 'Approval edited',
    module: 'APPROVALS',
    moduleLabel: 'Approvals',
    actor: priya,
    entityType: 'APPROVAL',
    entityTypeLabel: 'Approval',
    entityId: 5,
    entityLabel: 'APR-000005',
    parentType: null,
    parentId: null,
    details: null,
    ipAddress: null,
    userAgent: null,
    createdAt: '2026-10-16T05:00:00Z',
    ...overrides,
  };
}

const page = (content: AuditLogItem[], number = 0, totalPages = 1) => ({ content, page: number, size: 50, totalElements: content.length, totalPages });

const timeline = [
  entry({ id: 4, details: { changes: { title: { from: 'Licence', to: 'Licence renewal' }, amount: { from: 40000, to: 42000, finance: true } } } }),
  entry({ id: 3, action: 'APPROVAL_SUBMITTED', actionLabel: 'Approval submitted', actor: null, createdAt: '2026-10-15T05:00:00Z', details: { code: 'APR-000005' } }),
  entry({ id: 2, action: 'RECORD_CREATED', actionLabel: 'Approval created', actor: null, createdAt: '2026-10-15T05:00:01Z', details: { changes: { title: { from: null, to: 'Licence' }, requester: { from: null, to: 7, ref: 'USER', toLabel: 'Priya Menon' } } } }),
];

describe('RecordHistory', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows who changed what and when, with before and after values', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi({ 'GET /history/APPROVAL/5': (config) => (requests.push(config), page(timeline)) });
    renderPage(<RecordHistory type="APPROVAL" id={5} />, superAdmin);

    const list = await screen.findByRole('list', { name: 'History' });
    expect(requests[0]?.params).toEqual({ page: 0, size: 50 });
    const items = within(list).getAllByRole('article');
    expect(items).toHaveLength(2);

    const edit = within(list).getByRole('article', { name: 'Approval edited by Priya Menon' });
    expect(within(edit).getByText('Edited')).toBeInTheDocument();
    expect(within(edit).getByRole('time')).toHaveAttribute('dateTime', '2026-10-16T05:00:00Z');
    const changes = within(edit).getByLabelText('Changes');
    expect(changes).toHaveTextContent('Titlefrom Licenceto Licence renewal');
    expect(changes).toHaveTextContent('Amountfrom 40000to 42000');

    // The named event and the automatic entry of the same action are one item, made by the system.
    const created = within(list).getByRole('article', { name: 'Approval submitted by System' });
    expect(created).toHaveTextContent('System');
    expect(within(created).getByLabelText('Changes')).toHaveTextContent('RequesterPriya Menon');
  });

  it('hides finance amounts without Finance access', async () => {
    mockApi({ 'GET /history/APPROVAL/5': () => page(timeline) });
    renderPage(<RecordHistory type="APPROVAL" id={5} />, webEmployee);

    const edit = await screen.findByRole('article', { name: 'Approval edited by Priya Menon' });
    expect(within(edit).getByLabelText('Changes')).toHaveTextContent('AmountHidden (finance amount — needs Finance access)');
    expect(edit).not.toHaveTextContent('42000');
  });

  it('loads older changes on request', async () => {
    const user = userEvent.setup();
    const older = entry({ id: 1, actionLabel: 'Approval edited', createdAt: '2026-10-01T05:00:00Z', details: { changes: { dueDate: { from: null, to: '2026-10-30' } } } });
    mockApi({
      'GET /history/APPROVAL/5': (config) => ((config.params as { page: number }).page === 0 ? page([timeline[0]!], 0, 2) : page([older], 1, 2)),
    });
    renderPage(<RecordHistory type="APPROVAL" id={5} />, makeUser(['SUPER_ADMIN'], ['FINANCE_VIEW']));

    await screen.findByRole('list', { name: 'History' });
    await user.click(screen.getByRole('button', { name: 'Show older changes' }));
    expect(await screen.findByText(/2026-10-30/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Show older changes' })).not.toBeInTheDocument();
  });

  it('says when there is nothing yet, and when it fails', async () => {
    mockApi({ 'GET /history/TASK/1': () => page([]) });
    renderPage(<RecordHistory type="TASK" id={1} />, superAdmin);
    expect(await screen.findByText('No history yet')).toBeInTheDocument();
  });

  it('shows an error with a retry', async () => {
    mockApi({
      'GET /history/TASK/2': () => {
        throw new Error('boom');
      },
    });
    renderPage(<RecordHistory type="TASK" id={2} />, superAdmin);
    expect(await screen.findByText("Couldn't load the history")).toBeInTheDocument();
  });
});
