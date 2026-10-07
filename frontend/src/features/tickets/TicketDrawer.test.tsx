import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { slaStatus, ticketDetail } from '@/test/ticket-fixtures';

import { TicketDrawer } from './TicketDrawer';

const originalAdapter = api.defaults.adapter;

/** Karthik, the requester of the fixture ticket (id 4). */
const requester = makeUser(['EMPLOYEE'], ['TICKET_VIEW', 'TICKET_CREATE'], { id: 4, fullName: 'Karthik Raj', firstName: 'Karthik' });

const shared = {
  'GET /tickets/categories': () => [],
  'GET /departments': () => [],
  'GET /team': () => ({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
};

describe('TicketDrawer', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lets the requester confirm or reopen a resolved ticket', async () => {
    const resolved = ticketDetail({
      status: 'RESOLVED',
      resolvedAt: '2026-10-08T07:00:00Z',
      comments: [],
      sla: { firstResponse: slaStatus({ met: true }), resolution: slaStatus({ met: true }), overall: 'ON_TRACK' },
      permissions: { canWork: false, canAssign: false, canInternalNote: false, allowedStatuses: ['OPEN', 'CLOSED'] },
    });
    const adapter = mockApi({
      ...shared,
      'GET /tickets/42': () => resolved,
      'PUT /tickets/42/status': () => ({ ...resolved, status: 'CLOSED', closedAt: '2026-10-08T08:00:00Z' }),
    });

    renderPage(<TicketDrawer ticketId={42} onClose={() => {}} />, requester);

    expect(await screen.findByText('You raised this ticket')).toBeInTheDocument();
    expect(screen.getByLabelText('Status')).toBeDisabled();
    expect(screen.getAllByText('Met')).toHaveLength(2);
    await userEvent.click(screen.getByRole('button', { name: /Yes, close it/ }));

    await waitFor(() => {
      const put = adapter.mock.calls.find(([c]) => c.method === 'put' && c.url === '/tickets/42/status');
      expect(JSON.parse(put![0].data as string)).toEqual({ status: 'CLOSED' });
    });
  });

  it('requesters reply publicly and never get the internal-note option', async () => {
    const ticket = ticketDetail({
      comments: [ticketDetail().comments[1]!],
      permissions: { canWork: false, canAssign: false, canInternalNote: false, allowedStatuses: [] },
    });
    const adapter = mockApi({ ...shared, 'GET /tickets/42': () => ticket, 'POST /tickets/42/comments': () => ticket });

    renderPage(<TicketDrawer ticketId={42} onClose={() => {}} />, requester);

    await screen.findByText('Can you bring it to IT?');
    expect(screen.queryByLabelText(/Internal note/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Priority')).toBeDisabled();
    await userEvent.type(screen.getByLabelText('New reply'), 'Bringing it now');
    await userEvent.click(screen.getByRole('button', { name: 'Send reply' }));

    await waitFor(() => {
      const post = adapter.mock.calls.find(([c]) => c.method === 'post');
      expect(JSON.parse(post![0].data as string)).toEqual({ body: 'Bringing it now', internal: false });
    });
  });

  it('agents can add internal notes', async () => {
    const adapter = mockApi({ ...shared, 'GET /tickets/42': () => ticketDetail(), 'POST /tickets/42/comments': () => ticketDetail() });

    renderPage(<TicketDrawer ticketId={42} onClose={() => {}} />, superAdmin);

    await userEvent.click(await screen.findByLabelText(/Internal note \(hidden from the requester\)/));
    await userEvent.type(screen.getByLabelText('New internal note'), 'Ordered a replacement disk');
    await userEvent.click(screen.getByRole('button', { name: 'Add note' }));

    await waitFor(() => {
      const post = adapter.mock.calls.find(([c]) => c.method === 'post');
      expect(JSON.parse(post![0].data as string)).toEqual({ body: 'Ordered a replacement disk', internal: true });
    });
  });

  it('shows paused and overdue deadlines in words', async () => {
    mockApi({
      ...shared,
      'GET /tickets/42': () =>
        ticketDetail({
          status: 'WAITING_FOR_REQUESTER',
          firstRespondedAt: null,
          sla: {
            firstResponse: slaStatus({ state: 'BREACHED', remainingMinutes: -35, met: null }),
            resolution: slaStatus({ paused: true, remainingMinutes: 120 }),
            overall: 'BREACHED',
          },
        }),
    });

    renderPage(<TicketDrawer ticketId={42} onClose={() => {}} />, superAdmin);

    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('Overdue by 35m')).toBeInTheDocument();
    expect(within(dialog).getByText('Paused · 2h left')).toBeInTheDocument();
    expect(within(dialog).getByText(/clock paused while waiting/)).toBeInTheDocument();
  });
});
