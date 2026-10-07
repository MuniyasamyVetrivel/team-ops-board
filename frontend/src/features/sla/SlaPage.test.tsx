import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { ticketItem } from '@/test/ticket-fixtures';

import type { SlaPolicy, SlaSummary } from './api';
import SlaPage from './SlaPage';

const originalAdapter = api.defaults.adapter;

const policies: SlaPolicy[] = [
  { id: 1, name: 'Urgent', priority: 'URGENT', firstResponseMinutes: 60, resolutionMinutes: 240, version: 0 },
  { id: 2, name: 'High', priority: 'HIGH', firstResponseMinutes: 120, resolutionMinutes: 480, version: 3 },
  { id: 3, name: 'Medium', priority: 'MEDIUM', firstResponseMinutes: 240, resolutionMinutes: 1440, version: 0 },
  { id: 4, name: 'Low', priority: 'LOW', firstResponseMinutes: 480, resolutionMinutes: 2880, version: 0 },
];

const summary: SlaSummary = {
  windowDays: 30,
  generatedAt: '2026-10-08T10:00:00Z',
  warningThresholdPct: 75,
  created: 24,
  firstResponseCompliance: 92,
  resolutionCompliance: null,
  open: 7,
  onTrack: 4,
  warning: 2,
  breached: 1,
  paused: 1,
  priorities: [
    { priority: 'URGENT', firstResponseMinutes: 60, resolutionMinutes: 240, created: 3, firstResponseCompliance: 67, resolutionCompliance: 50, openBreached: 1 },
    { priority: 'HIGH', firstResponseMinutes: 120, resolutionMinutes: 480, created: 0, firstResponseCompliance: null, resolutionCompliance: null, openBreached: 0 },
    { priority: 'MEDIUM', firstResponseMinutes: 240, resolutionMinutes: 1440, created: 15, firstResponseCompliance: 100, resolutionCompliance: 93, openBreached: 0 },
    { priority: 'LOW', firstResponseMinutes: 480, resolutionMinutes: 2880, created: 6, firstResponseCompliance: 100, resolutionCompliance: 100, openBreached: 0 },
  ],
  atRisk: [ticketItem()],
};

describe('SlaPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows compliance, open states and "—" when nothing is decided', async () => {
    mockApi({ 'GET /sla/summary': () => summary, 'GET /sla/policies': () => policies });

    renderPage(<SlaPage />, superAdmin);

    const figures = await screen.findByRole('region', { name: 'SLA figures' });
    expect(await within(figures).findByText('92%')).toBeInTheDocument();
    expect(within(figures).getByText('Resolution met').closest('div')).toHaveTextContent('—');
    expect(within(figures).getByText('1 paused, waiting on requester')).toBeInTheDocument();
    const compliance = screen.getByRole('heading', { name: 'Compliance by priority' }).closest('[data-slot="card"]') as HTMLElement;
    const urgent = within(compliance).getByText('Urgent').closest('tr')!;
    expect(within(urgent).getByText('67%')).toBeInTheDocument();
    expect(within(urgent).getByText('4h')).toBeInTheDocument();
    expect(screen.getByText('Laptop will not boot')).toBeInTheDocument();
  });

  it('requests the chosen window', async () => {
    const adapter = mockApi({ 'GET /sla/summary': () => summary, 'GET /sla/policies': () => policies });

    renderPage(<SlaPage />, superAdmin);
    await screen.findByText('92%');
    await userEvent.selectOptions(screen.getByLabelText('Window'), '90');

    await waitFor(() => expect(adapter.mock.calls.filter(([c]) => c.url === '/sla/summary').at(-1)?.[0].params).toEqual({ days: 90 }));
  });

  it('lets SLA managers edit targets, validating like the server', async () => {
    const adapter = mockApi({
      'GET /sla/summary': () => summary,
      'GET /sla/policies': () => policies,
      'PUT /sla/policies/2': () => ({ ...policies[1], firstResponseMinutes: 90, version: 4 }),
    });

    renderPage(<SlaPage />, superAdmin);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit High targets' }));
    const dialog = await screen.findByRole('dialog');
    const first = within(dialog).getByLabelText(/First response/);

    await userEvent.clear(first);
    await userEvent.type(first, '600');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save targets' }));
    expect(await within(dialog).findByText('Resolution cannot be shorter than first response')).toBeInTheDocument();

    await userEvent.clear(first);
    await userEvent.type(first, '90');
    expect(within(dialog).getByText('= 1h 30m')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save targets' }));

    await waitFor(() => {
      const put = adapter.mock.calls.find(([c]) => c.method === 'put');
      expect(JSON.parse(put![0].data as string)).toEqual({ version: 3, firstResponseMinutes: 90, resolutionMinutes: 480 });
    });
  });

  it('hides editing from people without SLA_MANAGE', async () => {
    mockApi({ 'GET /sla/summary': () => summary, 'GET /sla/policies': () => policies });

    renderPage(<SlaPage />, webEmployee);

    await screen.findByText('92%');
    expect(screen.queryByRole('button', { name: /Edit .* targets/ })).not.toBeInTheDocument();
  });

  it('shows an error state with retry', async () => {
    mockApi({
      'GET /sla/summary': () => {
        throw new Error('boom');
      },
      'GET /sla/policies': () => policies,
    });

    renderPage(<SlaPage />, superAdmin);

    expect(await screen.findByText("Couldn't load SLA figures")).toBeInTheDocument();
  });
});
