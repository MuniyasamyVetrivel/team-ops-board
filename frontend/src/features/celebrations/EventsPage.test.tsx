import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { celebrationEditor, marketingEvent, page } from '@/test/celebration-fixtures';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import EventsPage from './EventsPage';

const originalAdapter = api.defaults.adapter;

const routes = {
  'GET /celebrations/upcoming': () => ({ today: '2026-10-12', from: '2026-10-12', to: '2026-10-25', items: [] }),
  'GET /celebrations/events': () =>
    page([marketingEvent(), marketingEvent({ id: 10, title: 'Sydney Testing Summit', eventType: 'UPCOMING_EVENT', category: 'EVENT', status: 'CANCELLED', location: 'ICC Sydney' })]),
};

describe('EventsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows each event with its category, status and LinkedIn post', async () => {
    mockApi(routes);
    renderPage(<EventsPage />, webEmployee);

    const webinar = (await screen.findByRole('button', { name: /Quality engineering/ })).closest('tr')!;
    expect(within(webinar).getByText('Webinar')).toBeInTheDocument();
    expect(within(webinar).getByText('Confirmed')).toBeInTheDocument();
    expect(within(webinar).getByText('Drafted')).toBeInTheDocument();
    const summit = screen.getByRole('button', { name: 'Sydney Testing Summit' }).closest('tr')!;
    expect(within(summit).getByText('Cancelled')).toBeInTheDocument();
    expect(within(summit).getByText('ICC Sydney')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /new event/i })).not.toBeInTheDocument();
  });

  it('offers a new event to editors', async () => {
    mockApi(routes);
    renderPage(<EventsPage />, celebrationEditor);
    expect(await screen.findByRole('button', { name: /new event/i })).toBeInTheDocument();
  });
});
