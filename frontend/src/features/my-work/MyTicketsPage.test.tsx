import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { ticketItem } from '@/test/ticket-fixtures';

import MyTicketsPage from './MyTicketsPage';

const originalAdapter = api.defaults.adapter;

const routes = {
  'GET /tickets/my/summary': () => ({ requestedOpen: 3, waitingOnMe: 1, resolvedToConfirm: 2, assignedOpen: 5 }),
  'GET /tickets': () => ({ content: [ticketItem()], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
};

describe('MyTicketsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows counts and lists the selected view', async () => {
    const adapter = mockApi(routes);

    renderPage(<MyTicketsPage />, webEmployee);

    const waiting = await screen.findByRole('tab', { name: /Waiting on me/ });
    await waitFor(() => expect(waiting).toHaveTextContent('1'));
    expect(screen.getByRole('tab', { name: /Raised by me/ })).toHaveTextContent('3');
    expect(screen.queryByRole('tab', { name: /Assigned to me/ })).not.toBeInTheDocument();

    await userEvent.click(waiting);
    await waitFor(() =>
      expect(adapter.mock.calls.filter(([c]) => c.url === '/tickets').at(-1)?.[0].params).toMatchObject({
        view: 'REQUESTED_BY_ME',
        status: ['WAITING_FOR_REQUESTER'],
      }),
    );
  });

  it('adds an "Assigned to me" view for agents', async () => {
    mockApi(routes);

    renderPage(<MyTicketsPage />, makeUser(['EMPLOYEE'], ['TICKET_VIEW', 'TICKET_CREATE', 'TICKET_EDIT']));

    const assigned = await screen.findByRole('tab', { name: /Assigned to me/ });
    await waitFor(() => expect(assigned).toHaveTextContent('5'));
  });
});
