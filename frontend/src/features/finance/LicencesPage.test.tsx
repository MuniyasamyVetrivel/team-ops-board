import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { CurrentUser } from '@/features/auth/types';
import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { financeContext, financeTool, financeViewer, licence, rate } from '@/test/finance-fixtures';
import { financeRoutes } from '@/test/finance-routes';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { SaveLicenceInput } from './api';
import LicencesPage from './LicencesPage';

const originalAdapter = api.defaults.adapter;

function renderLicences({ user = superAdmin, canEdit = true, path = '/tools/finance/licences', vendors = true }: { user?: CurrentUser; canEdit?: boolean; path?: string; vendors?: boolean } = {}) {
  const saved: SaveLicenceInput[] = [];
  const adapter = mockApi({
    'GET /department-tools': () => [financeTool(canEdit), ...(vendors ? [{ key: 'vendors', name: 'Vendors', icon: 'Store', description: '', canEdit: true }] : [])],
    'GET /modules/finance/licences': () => page([licence(), licence({ id: 8, name: 'Zoom', underUsed: false, utilisationPercent: 100, seatsUsed: 5, seatsPaid: 5, currency: 'EUR', annualCostAud: null, audRate: null, daysUntilRenewal: null })]),
    'GET /modules/finance/licences/7': () => licence(),
    'GET /modules/finance/imports': () => [],
    'GET /modules/finance/context': () => financeContext(),
    'GET /modules/finance/exchange-rates/current': () => [rate()],
    'GET /modules/vendors/vendors': () => page([]),
    'GET /departments': () => [{ id: 2, name: 'Engineering', code: 'ENG', description: null, status: 'ACTIVE', manager: null }],
    'POST /modules/finance/licences': (config) => {
      const input = JSON.parse(config.data as string) as SaveLicenceInput;
      saved.push(input);
      return licence({ id: 9, name: input.name });
    },
  });
  renderPage(financeRoutes('licences', <LicencesPage />), user, path);
  return { adapter, saved };
}

describe('LicencesPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists seats, costs and renewals, and flags under-use and a missing rate', async () => {
    renderLicences();

    const row = (await screen.findByRole('button', { name: 'Jira Software' })).closest('tr')!;
    expect(within(row).getByText('6 of 10 seats')).toBeInTheDocument();
    expect(within(row).getByText('Under-used')).toBeInTheDocument();
    expect(within(row).getByText('Renews in 18 days')).toBeInTheDocument();
    expect(within(row).getByText(/2,160\.00/)).toBeInTheDocument();

    const zoom = screen.getByRole('button', { name: 'Zoom' }).closest('tr')!;
    expect(within(zoom).getByText('No EUR rate')).toBeInTheDocument();
    expect(within(zoom).queryByText('Under-used')).not.toBeInTheDocument();
  });

  it('opens a licence from the URL with its assigned users', async () => {
    renderLicences({ path: '/tools/finance/licences?licence=7' });

    const seats = await screen.findByRole('region', { name: 'Seats' });
    expect(within(seats).getByText('4 seats free')).toBeInTheDocument();
    expect(within(within(seats).getByRole('list', { name: 'Assigned users' })).getByText('Arjun Rao')).toBeInTheDocument();
    expect(screen.getByText('Corporate Amex ···· 4242')).toBeInTheDocument();
    expect(screen.getByText(/at 1\.5 \(from/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit licence' })).toBeInTheDocument();
  });

  it('adds a licence in the default currency and refuses a card number as the payment method', async () => {
    const { saved } = renderLicences();
    await userEvent.click(await screen.findByRole('button', { name: 'New licence' }));

    const name = await screen.findByLabelText(/^Name/);
    expect(screen.getByLabelText(/^Currency/)).toHaveValue('INR');
    await userEvent.type(name, 'Slack');
    await userEvent.type(screen.getByLabelText(/^Payment method/), 'Visa 4111 1111 1111 1111');
    await userEvent.click(screen.getByRole('button', { name: 'Add licence' }));
    expect(await screen.findByText(/Never enter a card number/)).toBeInTheDocument();
    expect(saved).toHaveLength(0);

    await userEvent.clear(screen.getByLabelText(/^Payment method/));
    await userEvent.type(screen.getByLabelText(/^Payment method/), 'Corporate Visa');
    await userEvent.click(screen.getByRole('button', { name: 'Add licence' }));
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({ name: 'Slack', currency: 'INR', paymentMethod: 'Corporate Visa', billingCycle: 'MONTHLY', status: 'ACTIVE', assignedUserIds: [] });
  });

  it('hides editing from view-only users, and the vendor picker from those without Vendors', async () => {
    renderLicences({ user: financeViewer, canEdit: false, vendors: false });
    await screen.findByRole('button', { name: 'Jira Software' });

    expect(screen.queryByRole('button', { name: 'New licence' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Import CSV' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Export CSV/ })).toBeInTheDocument();
  });
});
