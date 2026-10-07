import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { DepartmentListItem } from '@/features/departments/api';
import type { SlaPolicy } from '@/features/sla/api';
import { api } from '@/lib/api/client';
import type { PageResponse } from '@/lib/api/types';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { ticketDetail, ticketItem } from '@/test/ticket-fixtures';

import TicketsPage from './TicketsPage';
import type { TicketCategory, TicketListItem } from './types';

const originalAdapter = api.defaults.adapter;

const page: PageResponse<TicketListItem> = { content: [ticketItem()], page: 0, size: 25, totalElements: 1, totalPages: 1 };

const categories: TicketCategory[] = [
  { id: 1, name: 'IT Support', description: 'General IT help', defaultDepartment: { id: 1, name: 'IT', code: 'IT' } },
  { id: 12, name: 'Other', description: 'Choose the team', defaultDepartment: null },
];

const departments: DepartmentListItem[] = [
  { id: 1, name: 'IT', code: 'IT', description: null, status: 'ACTIVE', manager: null, memberCount: 2, secondaryMemberCount: 0 },
  { id: 3, name: 'HR', code: 'HR', description: null, status: 'ACTIVE', manager: null, memberCount: 2, secondaryMemberCount: 0 },
];

const policies: SlaPolicy[] = [
  { id: 1, name: 'Urgent', priority: 'URGENT', firstResponseMinutes: 60, resolutionMinutes: 240, version: 0 },
  { id: 3, name: 'Medium', priority: 'MEDIUM', firstResponseMinutes: 240, resolutionMinutes: 1440, version: 0 },
];

const empty = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };

function routes(extra: Record<string, Parameters<typeof mockApi>[0][string]> = {}) {
  return {
    'GET /tickets': () => page,
    'GET /tickets/categories': () => categories,
    'GET /departments': () => departments,
    'GET /sla/policies': () => policies,
    'GET /team': () => empty,
    'GET /tickets/42': () => ticketDetail(),
    ...extra,
  };
}

describe('TicketsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists tickets with an SLA countdown that is labelled, not just coloured', async () => {
    mockApi(routes());

    renderPage(<TicketsPage />, superAdmin);

    const row = (await screen.findByText('Laptop will not boot')).closest('tr')!;
    expect(within(row).getByText('TKT-000042')).toBeInTheDocument();
    expect(within(row).getByText('Open')).toBeInTheDocument();
    expect(within(row).getByText('45m left')).toBeInTheDocument();
    expect(within(row).getByText('At risk:')).toHaveClass('sr-only');
    expect(within(row).getByText('Resolution')).toBeInTheDocument();
  });

  it('sends the open-status preset by default and the chosen filters', async () => {
    const adapter = mockApi(routes());

    renderPage(<TicketsPage />, superAdmin);
    await screen.findByText('Laptop will not boot');
    expect(adapter.mock.calls.find(([c]) => c.url === '/tickets')?.[0].params).toMatchObject({
      status: ['NEW', 'OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER'],
      sort: 'due,asc',
    });

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'all');
    await userEvent.selectOptions(screen.getByLabelText('View'), 'UNASSIGNED');
    await waitFor(() => {
      const params = adapter.mock.calls.filter(([c]) => c.url === '/tickets').at(-1)?.[0].params;
      expect(params).toMatchObject({ view: 'UNASSIGNED' });
      expect(params?.status ?? []).toEqual([]);
    });
  });

  it('opens the ticket drawer with the SLA panel and a marked internal note', async () => {
    mockApi(routes());

    renderPage(<TicketsPage />, superAdmin, '/tickets?ticket=42');

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByRole('heading', { name: 'Laptop will not boot' })).toBeInTheDocument();
    expect(within(drawer).getByRole('meter', { name: /Resolution: 81% of the target used/ })).toBeInTheDocument();
    expect(within(drawer).getByText('Internal note')).toBeInTheDocument();
    expect(within(drawer).getByText('Disk looks faulty')).toBeInTheDocument();
    const status = within(drawer).getByLabelText('Status');
    expect(within(status).getAllByRole('option').map((o) => o.textContent)).toEqual(['Open', 'In progress', 'Waiting for requester', 'Resolved', 'Closed']);
  });

  it('asks for a team only when the category has none, then raises the ticket', async () => {
    const adapter = mockApi(routes({ 'POST /tickets': () => ticketDetail({ status: 'NEW', assignee: null }) }));

    renderPage(<TicketsPage />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'New ticket' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/Subject/), 'Need a new keyboard');
    await userEvent.selectOptions(within(dialog).getByLabelText(/Category/), '1');
    expect(within(dialog).getByText('Handled by IT')).toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/Team/)).not.toBeInTheDocument();

    await userEvent.selectOptions(within(dialog).getByLabelText(/Category/), '12');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Raise ticket' }));
    expect(await within(dialog).findByText('Choose the team that should handle this')).toBeInTheDocument();

    await userEvent.selectOptions(within(dialog).getByLabelText(/Team/), '3');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Raise ticket' }));

    await waitFor(() => {
      const post = adapter.mock.calls.find(([c]) => c.method === 'post' && c.url === '/tickets');
      expect(JSON.parse(post![0].data as string)).toEqual({
        subject: 'Need a new keyboard',
        description: null,
        categoryId: 12,
        departmentId: 3,
        priority: 'MEDIUM',
      });
    });
  });

  it('shows the SLA targets for the chosen priority while raising a ticket', async () => {
    mockApi(routes());

    renderPage(<TicketsPage />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'New ticket' }));
    const dialog = await screen.findByRole('dialog');

    expect(await within(dialog).findByText('First response within 4h, resolution within 1d')).toBeInTheDocument();
    await userEvent.selectOptions(within(dialog).getByLabelText('Priority'), 'URGENT');
    expect(within(dialog).getByText('First response within 1h, resolution within 4h')).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    mockApi(routes({ 'GET /tickets': () => ({ ...page, content: [], totalElements: 0 }) }));

    renderPage(<TicketsPage />, superAdmin);

    expect(await screen.findByText('No open tickets')).toBeInTheDocument();
  });
});
